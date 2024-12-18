package scalangband.model

import org.slf4j.LoggerFactory
import scalangband.model.action.GameAction
import scalangband.model.action.result.ActionResult
import scalangband.model.fov.FieldOfViewCalculator
import scalangband.model.level.{Level, LevelAccessor, LevelCallback, LevelGenerator, RandomWeightedLevelGenerator, Town}
import scalangband.model.location.Coordinates
import scalangband.model.monster.Monster
import scalangband.model.scheduler.SchedulerQueue
import scalangband.model.settings.Settings
import scalangband.model.tile.{DownStairs, OccupiableTile, Tile, UpStairs}
import scalangband.model.util.RandomUtils.randomElement
import scalangband.model.util.TileUtils.allCoordinatesFor

import scala.util.Random

class Game(seed: Long, val random: Random, val settings: Settings, val player: Player, val town: Level, var level: Level, var turn: Int) {
  private val logger = LoggerFactory.getLogger(this.getClass)

  private val fov = new FieldOfViewCalculator(20)
  fov.recompute(player.coordinates, town)

  val levelGenerator: LevelGenerator = RandomWeightedLevelGenerator()

  private var queue: SchedulerQueue = SchedulerQueue(level.creatures)

  val accessor: GameAccessor = new GameAccessor(this)
  val callback: GameCallback = new GameCallback(this)

  def takeTurn(playerAction: GameAction): Seq[ActionResult] = {
    // We know(?) that the player is at the head of the queue
    logger.info(s"Player is taking  $playerAction")

    val playerActionResult: Option[ActionResult] = playerAction.apply(accessor, callback)
    var results: List[Option[ActionResult]] = List(playerActionResult)
    
    if (playerAction.energyRequired > 0) {
      val player = queue.poll()
      player.deductEnergy(playerAction.energyRequired)
      queue.insert(player)
      
      results = takeMonsterActions() ::: results
    }
    
    fov.recompute(player.coordinates, level)

    results.flatten.reverse
  }

  private def takeMonsterActions(): List[Option[ActionResult]] = {
    if (queue.peek.energy <= 0) startNextTurn()
    
    var results = List.empty[Option[ActionResult]]
    while (queue.peek.isInstanceOf[Monster]) {
      val monster = queue.poll().asInstanceOf[Monster]
      val action: GameAction = monster.getAction(level)
      logger.info(s"${monster.name} is taking action $action")
      val result: Option[ActionResult] = action.apply(accessor, callback)
      monster.deductEnergy(action.energyRequired)
      queue.insert(monster)

      results = result :: results

      if (queue.peek.energy <= 0) startNextTurn()
    }
    results
  }
  
  def startNextTurn(): Unit = {
    turn = turn + 1
    level.startNextTurn()
    
    queue = SchedulerQueue(level.creatures)
  }
}
object Game {
  val BaseEnergyUnit: Int = 20
  val MaxDungeonDepth: Int = 100

  def newGame(seed: Long, random: Random, settings: Settings, player: Player): Game = {
    val town: Level = Town(random)

    val start = randomElement(random, allCoordinatesFor(town.tiles, tile => tile.isInstanceOf[DownStairs]))
    town.addPlayer(start, player)

    new Game(seed, random, settings, player, town, town, 0)
  }
}

class GameAccessor(private val game: Game) {
  // this has to be a `def` since the current level of the game is mutable
  def level: LevelAccessor = new LevelAccessor(game.level)
  val player: PlayerAccessor = new PlayerAccessor(game.player)

  def playerTile: OccupiableTile = level.tile(player.coordinates).asInstanceOf[OccupiableTile]
}

class GameCallback(private val game: Game) {
  // this has to be a `def` since the current level of the game is mutable
  def level: LevelCallback = new LevelCallback(game.level)
  val player: PlayerCallback = new PlayerCallback(game.player)

  def movePlayerTo(targetCoordinates: Coordinates): Unit = {
    game.level.moveOccupant(game.player.coordinates, targetCoordinates)
  }

  def killMonster(monster: Monster): Unit = {
    game.level(monster.coordinates).asInstanceOf[OccupiableTile].clearOccupant()
  }

  def moveDownTo(depth: Int): Unit = {
    val newLevel = game.levelGenerator.generateLevel(game.random, game.level.depth + 1)
    val startingCoordinates =
      randomElement(game.random, allCoordinatesFor(newLevel.tiles, tile => tile.isInstanceOf[UpStairs]))
    newLevel.addPlayer(startingCoordinates, game.player)
    game.level = newLevel
  }

  def moveUpTo(depth: Int): Unit = {
    val newLevel = if (game.level.depth == 1) {
      game.town
    } else {
      game.levelGenerator.generateLevel(game.random, game.level.depth)
    }
    val startingCoordinates =
      randomElement(game.random, allCoordinatesFor(newLevel.tiles, tile => tile.isInstanceOf[DownStairs]))
    newLevel.addPlayer(startingCoordinates, game.player)
    game.level = newLevel
  }
}
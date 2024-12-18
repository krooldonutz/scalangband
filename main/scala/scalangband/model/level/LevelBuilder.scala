package scalangband.model.level

import scalangband.model.Game.MaxDungeonDepth
import scalangband.model.location.Coordinates
import scalangband.model.monster.Monster
import scalangband.model.tile.{DownStairs, Floor, OccupiableTile, PermanentWall, RemovableWall, Tile, UpStairs}
import scalangband.model.util.RandomUtils.randomElement
import scalangband.model.util.TileUtils.allCoordinatesFor

import scala.util.Random

class LevelBuilder(val tiles: Array[Array[Tile]], val depth: Int) {
  def height: Int = tiles.length
  def width: Int = tiles(0).length

  def apply(row: Int, col: Int): Tile = tiles(row)(col)
  def setTile(coordinates: Coordinates, tile: Tile): Unit = setTile(coordinates.row, coordinates.col, tile)
  def setTile(row: Int, col: Int, tile: Tile): Unit = tiles(row)(col) = tile

  def setMonster(row: Int, col: Int, createMonster: Coordinates => Monster): Unit = {
    this(row, col).asInstanceOf[OccupiableTile].setOccupant(createMonster(Coordinates(row, col)))
  }
  
  def build(random: Random, createTown: (Int, Array[Array[Tile]]) => Level): Level = {
    enforceStairInvariants(random)

    createTown(depth, tiles)
  }

  /**
   * Enforces the correct number of up and down stairs for the level's depth
   */
  private def enforceStairInvariants(random: Random): Unit = {
    enforceUpStairsInvariants(random)
    enforceDownStairsInvariants(random)
  }

  private def enforceUpStairsInvariants(random: Random): Unit = {
    val upStairs = allCoordinatesFor(tiles, _.isInstanceOf[UpStairs])
    if (depth == 0) {
      // replace any up stairs on the town level with floor tiles
      upStairs.foreach(coords => setTile(coords, Floor.empty()))
    } else if (upStairs.length == 0) {
      (0 until desiredNumberOfUpStairs(random)).foreach { _ =>
        val floor = randomElement(random, allCoordinatesFor(tiles, tile => tile.isInstanceOf[Floor] && !tile.occupied))
        setTile(floor, new UpStairs())
      }
    }
  }

  private def desiredNumberOfUpStairs(random: Random) = {
    random.nextInt(2) + 1
  }

  private def enforceDownStairsInvariants(random: Random): Unit = {
    val downStairs = allCoordinatesFor(tiles, _.isInstanceOf[DownStairs])
    if (depth == MaxDungeonDepth) {
      // replace any up stairs on the bottom level with floor tiles
      downStairs.foreach(coords => setTile(coords, Floor.empty()))
    } else if (downStairs.length == 0) {
      (0 until desiredNumberOfDownStairs(depth, random)).foreach { _ =>
        val floor = randomElement(random, allCoordinatesFor(tiles, tile => tile.isInstanceOf[Floor] && !tile.occupied))
        setTile(floor, new DownStairs())
      }
    }
  }

  private def desiredNumberOfDownStairs(depth: Int, random: Random) = {
    if (depth == 0) 1 else random.nextInt(3) + 1
  }
}
object LevelBuilder {

  /**
   * Creates a new builder of the given height and width. The builder starts out as a boundary of a [[PermanentWall]],
   * filled by [[RemovableWall]]s.
   */
  def apply(height: Int, width: Int, depth: Int): LevelBuilder = {
    val tiles = Array.ofDim[Tile](height, width)

    for (row <- 0 until height) {
      for (col <- 0 until width) {
        tiles(row)(col) = {
          if (row == 0 || col == 0 || row == height - 1 || col == width - 1) new PermanentWall() else new RemovableWall()
        }
      }
    }

    new LevelBuilder(tiles, depth)
  }

  /**
   * Generates a random sized builder, twice as wide as it is tall, since this seems to be a pleasing ratio when
   * rendered in a monospace font.
   */
  def randomSizedLevelBuilder(random: Random, depth: Int): LevelBuilder = {
    val minWidth: Int = 80
    val maxWidth: Int = 120
    val increments = 4

    val increment = random.nextInt((maxWidth - minWidth) / increments)
    val width = minWidth + (increment * increments)
    val height = width / 2

    LevelBuilder(height, width, depth)
  }
}
package scalangband.ui.keys

import scalangband.model.action.GameAction
import scalangband.ui.GamePanelCallback

import scala.swing.event.KeyPressed

trait KeyHandler {
  def handleKeyPressed(event: KeyPressed, callback: GamePanelCallback): Option[GameAction]
}

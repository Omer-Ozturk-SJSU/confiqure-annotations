package ai.confiqure

import scala.annotation.StaticAnnotation

/** Marks a class as a confiqure.ai configuration target.
  *
  * Usage:
  * {{{
  * @Confiqure(
  *   end      = "/notifications",
  *   `type`   = "single",
  *   tools    = Array("Amazon_Connect", "Ebay_Connect"),
  *   callback = "https://myapp.com/webhooks/confiqure"
  * )
  * class Notifications { /* ... */ }
  * }}}
  *
  * The confiqure CLI scans for this annotation; the backend AI parses the
  * annotated class and generates the chat playbook.
  *
  * Note: `type` is wrapped in backticks because it is a Scala reserved word.
  */
final class Confiqure(
    val end: String = "",
    val `type`: String = "single",
    val tools: Array[String] = Array.empty,
    val callback: String = ""
) extends StaticAnnotation

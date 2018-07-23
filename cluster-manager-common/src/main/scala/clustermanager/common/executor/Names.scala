package clustermanager.common.executor

import scala.util.Random

/**
  * Names for executor binaries
  */
private[clustermanager] object Names {
  final val names = Seq(
    "terminator",
    "neo",
    "han_solo",
    "joker",
    "batman",
    "jack_sparrow",
    "james_bond",
    "yoda",
    "gandalf",
    "forrest_gump",
    "harry_potter",
    "luke_skywalker",
    "legolas",
    "marty_mcfly",
    "the_dude",
    "shrek",
    "gollum",
    "mr_bean",
    "darth_vader",
    "ace_ventura"
  )

  def random(): String = {
    names(Random.nextInt(names.length))
  }
}

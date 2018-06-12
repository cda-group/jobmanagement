package utils

import scala.collection.mutable

/** The SlotHashMap utilises a set fixed slot size
  */
class SlotHashMap[A,B](initSize: Int) extends mutable.HashMap[A,B] {
  override def initialSize: Int = initSize
}

package com.typesafe.conductr.sandbox

package object sbt {

  implicit class MapOps[A, B >: String](left: Map[A, B]) {

    def merge(right: Map[A, B], separator: String = " "): Map[A, B] =
      right.foldLeft(left) {
        case (acc, (k, v)) =>
          acc + (k -> s"${acc.get(k).map(_ + separator + v).getOrElse(v)}")
      }
  }
}

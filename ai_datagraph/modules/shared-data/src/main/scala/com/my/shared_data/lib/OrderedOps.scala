package com.my.shared_data.lib

import org.tessellation.schema.address.Address
import com.my.shared_data.schema.Updates._
import scala.math.Ordering

object OrderedOps {

  // object implicits {
  //   implicit val addressOrdering: Ordering[Address] = Ordering.by(_.value.value)
  // }
  object implicits {
    implicit val DataUpdateOnChainOrdering: Ordering[DataUpdateOnChain] =
      new Ordering[DataUpdateOnChain] {
        def compare(x: DataUpdateOnChain, y: DataUpdateOnChain): Int = {
          x.timestamp.compare(y.timestamp)
        }
      }
  }  
}

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intel.analytics.bigdl.utils

import com.intel.analytics.bigdl.nn.abstractnn.Activity

import scala.collection.mutable
import scala.collection.mutable.Map

/**
 * Simulate the Table data structure in lua
 *
 * @param state
 * @param topIndex
 */
class Table private[bigdl](
  state: Map[Any, Any] = new mutable.HashMap[Any, Any](),
  // index of last element in the contiguous numeric number indexed elements start from 1
  private var topIndex: Int = 0
) extends Serializable with Activity {

  private[bigdl] def this(data: Array[Any]) = {
    this(new mutable.HashMap[Any, Any](), 0)
    while (topIndex < data.length) {
      state.put(topIndex + 1, data(topIndex))
      topIndex += 1
    }
  }

  def getState(): Map[Any, Any] = this.state

  def get[T](key: Any): Option[T] = {
    if (!state.contains(key)) {
      return None
    }

    Option(state(key).asInstanceOf[T])
  }

  def contains(key: Any): Boolean = {
    state.contains(key)
  }

  def apply[T](key: Any): T = {
    state(key).asInstanceOf[T]
  }

  def update(key: Any, value: Any): this.type = {
    state(key) = value
    if (key.isInstanceOf[Int] && topIndex + 1 == key.asInstanceOf[Int]) {
      topIndex += 1
      while (state.contains(topIndex + 1)) {
        topIndex += 1
      }
    }
    this
  }

  override def clone(): Table = {
    val result = new Table()

    for (k <- state.keys) {
      result(k) = state.get(k).get
    }

    result
  }

  override def toString: String = {
    s" {\n\t${state.map{case (key: Any, value: Any) =>
      s"$key: " + s"$value".split("\n").mkString(s"\n\t${key.toString.replaceAll(".", " ")}  ")
    }.mkString("\n\t")}\n }"
  }

  override def equals(obj: Any): Boolean = {
    if (obj == null) {
      return false
    }
    if (!obj.isInstanceOf[Table]) {
      return false
    }
    val other = obj.asInstanceOf[Table]
    if (this.eq(other)) {
      return true
    }
    if (this.state.keys.size != other.getState().keys.size) {
      return false
    }
    this.state.keys.foreach(key => {
      if (this.state(key) != other.getState()(key)) {
        return false
      }
    })
    true
  }

  override def hashCode() : Int = {
    val seed = 37
    var hash = 1

    this.state.keys.foreach(key => {
      hash = hash * seed + key.hashCode()
      hash = hash * seed + this.state(key).hashCode()
    })

    hash
  }

  def remove[T](index: Int): Option[T] = {
    require(index > 0)

    if (topIndex >= index) {
      var i = index
      val result = state(index)
      while (i < topIndex) {
        state(i) = state(i + 1)
        i += 1
      }
      state.remove(topIndex)
      topIndex -= 1
      Some(result.asInstanceOf[T])
    } else if (state.contains(index)) {
      state.remove(index).asInstanceOf[Option[T]]
    } else {
      None
    }
  }

  def remove[T](): Option[T] = {
    if (topIndex != 0) {
      remove[T](topIndex)
    } else {
      None
    }
  }

  def insert[T](obj: T): this.type = update(topIndex + 1, obj)

  def insert[T](index: Int, obj: T): this.type = {
    require(index > 0)

    if (topIndex >= index) {
      var i = topIndex + 1
      topIndex += 1
      while (i > index) {
        state(i) = state(i - 1)
        i -= 1
      }
      update(index, obj)
    } else {
      update(index, obj)
    }

    this
  }

  def add(other: Table): this.type = {
    for (s <- other.getState().keys) {
      require(s.isInstanceOf[String])
      this.state(s) = other(s)
    }
    this
  }

  def length(): Int = state.size

  def save(path : String, overWrite : Boolean): this.type = {
    File.save(this, path, overWrite)
    this
  }

  /**
   * Recursively flatten the table to a single table containing no nested table inside
 *
   * @return the flatten table
   */
  def flatten(): Table = {
    flatten(1)
  }

  private def flatten(startIndex: Int): Table = {
    var resultIndex = startIndex
    var i = 1
    val newState = mutable.Map[Any, Any]()

    while (i <= state.size) {
      state.get(i).get match {
        case table: Table =>
          val newTable = table.flatten(resultIndex)
          newState ++= newTable.getState()
          resultIndex = newState.size
        case other =>
          newState.put(resultIndex, other)
      }
      resultIndex += 1
      i += 1
    }
    new Table(newState)
  }

  /**
   * Recursively inverse flatten the flatten table to the same shape with target
 *
   * @param target the target shape to become
   * @return the inverse flatten the table with the same shape with target
   */
  def inverseFlatten(target: Table): Table = {
    inverseFlatten(target, 1)
  }

  /**
   * Recursively inverse flatten the flatten table to the same shape with target
 *
   * @param target the target shape to become
   * @param startIndex for each iteration the start index as an offset
   * @return the inverse flatten the table with the same shape with target
   */
  private def inverseFlatten(target: Table, startIndex: Int): Table = {
    var i = 1
    var resultIndex = startIndex
    val newState = mutable.Map[Any, Any]()

    while (i <= target.getState().size) {
      target.getState().get(i).get match {
        case table: Table =>
          val newTable = inverseFlatten(table, resultIndex)
          newState.put(i, new Table(newTable.getState()))
          resultIndex += newTable.getState().size
        case other =>
          newState.put(i, state.get(resultIndex).get)
      }
      i += 1
      resultIndex += 1
    }

    new Table(newState)
  }
}

object T {
  def apply(): Table = {
    new Table()
  }

  /**
   * Construct a table from a sequence of value.
   *
   * The index + 1 will be used as the key
   */
  def apply(data1: Any, datas: Any*): Table = {
    val firstElement = Array(data1)
    val otherElements = datas.toArray
    new Table(firstElement ++ otherElements)
  }

  /**
   * Construct a table from an array
   *
   * The index + 1 will be used as the key
   *
   * @param data
   * @return
   */
  def array(data: Array[Any]): Table = {
    new Table(data)
  }

  /**
   * Construct a table from a sequence of pair.
   */
  def apply(tuple: Tuple2[Any, Any], tuples: Tuple2[Any, Any]*): Table = {
    val table = new Table()
    table(tuple._1) = tuple._2
    for ((k, v) <- tuples) {
      table(k) = v
    }
    table
  }

  def load(path : String) : Table = {
    File.load(path)
  }
}

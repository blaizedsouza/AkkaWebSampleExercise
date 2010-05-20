package org.chicagoscala.awse.server.persistence
import org.chicagoscala.awse.server._
import org.chicagoscala.awse.persistence._
import org.chicagoscala.awse.persistence.inmemory._
import org.chicagoscala.awse.persistence.mongodb._
import se.scalablesolutions.akka.actor.Transactor
import se.scalablesolutions.akka.stm.Transaction._
import se.scalablesolutions.akka.util.Logging
import net.liftweb.json.JsonAST._
import net.liftweb.json.JsonDSL._
import org.joda.time._

/**
 * DataStorageServer manages storage of time-oriented data, stored as JSON.
 */
class DataStorageServer(val service: String) 
    extends Transactor with NamedActor with Logging {

  val actorName = "DataStoreServer("+service+")"
      
  log.info("Creating: "+actorName)
  
  def receive = {
    case Get(fromTime, untilTime) => 
      reply(getData(fromTime, untilTime))
            
    case Put(time, json) => reply(putData(time, json))

    case Finished => 
      val msg = actorName + ": Received Finished message."
      log.ifInfo (msg)
      reply (("message", msg))

    case x => 
      val msg = actorName + ": unknown message received: " + x
      log.ifInfo (msg)
      reply (("error", msg))
  }
    
  protected[persistence] def getData(fromTime: DateTime, untilTime: DateTime) = try {
    val data = for {
      (timeStamp, json) <- dataStore.range(fromTime, untilTime)
    } yield json
    val result = toJSON(data toList)
    log.ifDebug(actorName + ": GET returning response for startTime, endTime, size = " + 
      fromTime + ", " + untilTime + ", " + result.size)
    result
  } catch {
    case th => 
      log.error(actorName + ": Exception thrown: ", th)
      th.printStackTrace
      throw th
  }
  
  var putCount = 0
  
  protected[persistence] def putData(time: DateTime, json: String) = {
    if (putCount % 10 == 0)
      log.info(actorName + " PUT: storing 10th Pair(" + time + ", " + json.substring(0, 100) + " ...)")
    else
      log.ifTrace (actorName + ": PUT: storing Pair(" + time + ", " + json.substring(0, 100) + " ...)")
    putCount += 1
      
    try {
      dataStore.add(Pair(time, json))
      Pair("message", "Put received for time " + time + ". Data storage started.")
    } catch {
      case ex => 
        log.error(actorName + ": PUT: exception thrown while attempting to add JSON to the data store: "+json)
        ex.printStackTrace();
        throw ex
    }
  }

  protected def toJSON(data: List[String]) = data.size match {
    case 0 => "[]"
    case _ => "[" + data.reduceLeft(_ + ", " + _) + "]"
  }
  
  protected lazy val dataStore = DataStorageServer.makeDefaultDataStore(service)  
}

object DataStorageServer extends Logging {

  import se.scalablesolutions.akka.config.Config.config

  /**
   * Instantiate the default type of datastore: an InMemoryDataStore with an upper limit on values.
   */
  def makeDefaultDataStore(storeName: String): DataStore[String] = {
    val db = System.getProperty("app.datastore.type", config.getString("app.datastore.type", "mongodb"))
    if (db.toLowerCase.trim == "mongodb") {
      log.ifInfo("Using MongoDB-backed data storage.")
      new MongoDBDataStore(storeName)
    } else {
      log.ifInfo("Using in-memory data storage.")
      new InMemoryDataStore[String](storeName)
    }
  }
}
/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.dotme.liquidtpl.lib.memcache

import com.google.appengine.api.datastore.DatastoreServiceFactory
import com.google.appengine.api.datastore.DatastoreTimeoutException
import com.google.appengine.api.datastore.Entity
import com.google.appengine.api.datastore.FetchOptions
import com.google.appengine.api.datastore.Query
import com.google.appengine.api.memcache.MemcacheService
import com.google.appengine.api.memcache.MemcacheServiceFactory
import java.util.logging.Level
import java.util.logging.Logger
import scala.collection.JavaConversions._

object ReverseCounterLogService {
  val MC_KEY_COUNTER = "org.dotme.liquidtpl.service.ReverseCounterLogService#counter_";
  val COUNTER_KEY = "c";
  val COUNTER_KIND_PREFIX = "rc_";
  val CLEANUP_AT_ONCE = 100;
  val KEY_NAME = "name"

  private val mcService: MemcacheService = MemcacheServiceFactory
    .getMemcacheService();

  private val log: Logger = Logger.getLogger(ReverseCounterLogService.getClass.getName)

  /**
   * Increments the counter value and returns it. The values will be unique
   * serial numbers across the application. The value is generated by
   * {@link MemcacheService#increment(Object, long)} and recorded by
   * {@link ReverseCounterLog} entities so that the counter may have durability while
   * it is scalable. You may need to add a cron task to delete the old
   * ReverseCounterLog entities at background, if the entities are growing too big.
   *
   * @return an incremented counter value
   */
  def increment(name: String): Long = {

    // try to get the next value from Memcache
    val countValue: Long =
      try {
        mcService.increment(MC_KEY_COUNTER + name, -1).longValue;
      } catch {
        case e =>
          log.log(Level.WARNING, "Failed to increment on Memcache: ", e);
          // if failed, restore the value from Log
          restoreCountValue(name);
      }

    // save a countValue by a ReverseCounterLog
    try {
      saveReverseCounterLog(countValue, name);
    } catch {
      case e: DatastoreTimeoutException => log.log(Level.WARNING, "Failed to save ReverseCounterLog: ", e);
    }

    // return the value
    return countValue;
  }

  // save a count value by a ReverseCounterLog
  private def saveReverseCounterLog(countValue: Long, name: String): Unit = {
    val datastoreService = DatastoreServiceFactory.getDatastoreService
    val e: Entity = new Entity(COUNTER_KIND_PREFIX + name)
    e.setProperty(COUNTER_KEY, countValue)
    datastoreService.put(e)
  }

  // restore the largest count value from Datastore
  def restoreCountValue(name: String): Long = {
    val datastoreService = DatastoreServiceFactory.getDatastoreService
    val cls: List[Entity] = datastoreService.prepare(new Query(COUNTER_KIND_PREFIX + name)
      .addSort(COUNTER_KEY, Query.SortDirection.ASCENDING)).asList(
      FetchOptions.Builder.withLimit(1)).toList

    // restore count value if it can
    val countValue: Long = if (cls == null || cls.size == 0) {
      Long.MaxValue
    } else {
      cls.apply(0).getProperty(COUNTER_KEY).asInstanceOf[Long] - 1;
    }

    // save the new value to Memcache
    mcService.put(MC_KEY_COUNTER + name, countValue);
    log.log(Level.WARNING, "Restored count value: " + countValue);

    // return the value
    return countValue;
  }

  /**
   * Deletes the count value stored on Memcache. This should be called only
   * for testing purpose.
   */
  def deleteCountValueOnMemcache(name: String) {
    mcService.delete(MC_KEY_COUNTER + name);
  }

  def cleanupDatastore(name: String) {
    var isFirst = true;
    val datastoreService = DatastoreServiceFactory.getDatastoreService
    datastoreService.prepare(new Query(COUNTER_KIND_PREFIX + name)
      .addSort(COUNTER_KEY, Query.SortDirection.ASCENDING)).asList(
      FetchOptions.Builder.withLimit(CLEANUP_AT_ONCE)).foreach { e =>
        {
          if (!isFirst) {
            datastoreService.delete(e.getKey)
          }
          isFirst = false
        }
      }
  }
}

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.dotme.liquidtpl.controller
import scala.collection.JavaConversions._
import dispatch.json.JsObject
import dispatch.json.JsString
import dispatch.json.JsValue
import sjson.json.JsonSerialization._
import sjson.json.DefaultProtocol._
import org.dotme.liquidtpl.Constants
import org.dotme.liquidtpl.LanguageUtil
import org.dotme.liquidtpl.helper.BasicHelper

abstract class AbstractJsonCommitController extends AbstractJsonController with ControllerError {
  override def getJson: JsValue = {
    val result = request.getParameter(Constants.KEY_MODE) match {
      case Constants.MODE_SUBMIT => {
        val values = if (validate() && update()) {
          if (redirectUri.isEmpty) {
            JsObject(List(
              (JsString(Constants.KEY_RESULT), tojson(Constants.RESULT_SUCCESS))))
          } else {
            JsObject(List(
              (JsString(Constants.KEY_RESULT), tojson(Constants.RESULT_SUCCESS)),
              (JsString(Constants.KEY_REDIRECT), tojson(redirectUri))))
          }
        } else {
          JsObject(List(
            (JsString(Constants.KEY_RESULT), tojson(Constants.RESULT_FAILURE)),
            (JsString(Constants.KEY_ERRORS), tojson(getErrorList))))
        }
        BasicHelper.writeJsonCommentFiltered(response, values)
        null
      }
      case _ => JsObject()
    }
    result
  }

  def redirectUri: String = null;
  def validate(): Boolean
  def update(): Boolean

}
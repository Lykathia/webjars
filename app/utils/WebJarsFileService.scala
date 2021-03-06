package utils

import java.io.FileNotFoundException

import play.api.http.Status
import play.api.libs.ws.WS
import play.api.Play.current

import Memcache._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

object WebJarsFileService {

  private def fetchFileList(groupId: String, artifactId: String, version: String): Future[List[String]] = {
    WS.url(s"http://webjars-file-service.herokuapp.com/listfiles/$groupId/$artifactId/$version").get().flatMap { response =>
      response.status match {
        case Status.OK =>
          response.json.asOpt[List[String]].fold(Future.failed[List[String]](new Exception("")))(Future.successful)
        case Status.NOT_FOUND =>
          Future.failed(new FileNotFoundException(response.body))
        case _ =>
          Future.failed(new Exception(response.body))
      }
    }
  }

  def getFileList(groupId: String, artifactId: String, version: String): Future[List[String]] = {
    val cacheKey =  groupId + "-" + artifactId + "-" + version + "-files"
    Global.memcached.get[List[String]](cacheKey).flatMap { maybeFileList =>
      maybeFileList.map(Future.successful).getOrElse {
        val fileListFuture = WebJarsFileService.fetchFileList(groupId, artifactId, version)
        fileListFuture.foreach { fileList =>
          Global.memcached.set(cacheKey, fileList, Duration.Inf)
        }
        fileListFuture
      }
    }
  }

}

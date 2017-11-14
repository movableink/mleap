package ml.combust.mleap.serving

import akka.actor.ActorSystem
import akka.event.Logging.LogLevel
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.model.{HttpRequest, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ExceptionHandler, RouteResult}
import akka.http.scaladsl.server.directives.LoggingMagnet
import ml.combust.mleap.runtime.DefaultLeapFrame
import ml.combust.mleap.serving.domain.v1._
import ml.combust.mleap.serving.marshalling.ApiMarshalling._
import ml.combust.mleap.serving.marshalling.LeapFrameMarshalling._

/**
  * Created by hollinwilkins on 1/30/17.
  */
class MleapResource(service: MleapService)
                   (implicit system: ActorSystem) {
  private def recordLog(logger: LoggingAdapter, level: LogLevel)(req: HttpRequest)(res: RouteResult): Unit = res match {
    case RouteResult.Complete(x) =>
      logger.log(level, logger.format("status={} scheme=\"{}\" path=\"{}\" method=\"{}\"",
        x.status.intValue(), req.uri.scheme, req.uri.path, req.method.value))
    case RouteResult.Rejected(rejections) => // no logging required
  }

  def errorHandler(logger: LoggingAdapter): ExceptionHandler = ExceptionHandler {
    case e: Throwable =>
      logger.error(e, "error with request")
      complete((StatusCodes.InternalServerError, e))
  }

  val logger = Logging.getLogger(system.eventStream, classOf[MleapResource])

  val routes = handleExceptions(errorHandler(logger)) {
    withLog(logger) {
      logRequestResult(LoggingMagnet(recordLog(_, Logging.InfoLevel))) {
        path("model_4894") {
          put {
            entity(as[LoadModelRequest]) {
              request =>
                complete(service.loadModel(request, 4894))
            }
          } ~ delete {
            complete(service.unloadModel(UnloadModelRequest(), 4894))
          } ~ get {
            complete(service.getSchema(4894))
          }
        } ~ path("transform_4894") {
          post {
            entity(as[DefaultLeapFrame]) {
              frame => complete(service.transform(frame, 4894))
            }
          }
        } ~ path("model_6323") {
          put {
            entity(as[LoadModelRequest]) {
              request =>
                complete(service.loadModel(request, 6323))
            }
          } ~ delete {
            complete(service.unloadModel(UnloadModelRequest(), 6323))
          } ~ get {
            complete(service.getSchema(6323))
          }
        } ~ path("transform_6323") {
          post {
            entity(as[DefaultLeapFrame]) {
              frame => complete(service.transform(frame, 6323))
            }
          }
        } ~ path("model_6317") {
          put {
            entity(as[LoadModelRequest]) {
              request =>
                complete(service.loadModel(request, 6317))
            }
          } ~ delete {
            complete(service.unloadModel(UnloadModelRequest(), 6317))
          } ~ get {
            complete(service.getSchema(6317))
          }
        } ~ path("transform_6317") {
          post {
            entity(as[DefaultLeapFrame]) {
              frame => complete(service.transform(frame, 6317))
            }
          }
        } ~ path("model_6757") {
          put {
            entity(as[LoadModelRequest]) {
              request =>
                complete(service.loadModel(request, 6757))
            }
          } ~ delete {
            complete(service.unloadModel(UnloadModelRequest(), 6757))
          } ~ get {
            complete(service.getSchema(6757))
          }
        } ~ path("transform_6757") {
          post {
            entity(as[DefaultLeapFrame]) {
              frame => complete(service.transform(frame, 6757))
            }
          }
        }
      }
    }
  }
}

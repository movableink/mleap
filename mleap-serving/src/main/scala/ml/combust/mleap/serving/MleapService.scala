package ml.combust.mleap.serving

import java.io.File

import ml.combust.bundle.BundleFile
import ml.combust.bundle.dsl.Bundle
import ml.combust.mleap.core.types.StructType
import ml.combust.mleap.runtime.DefaultLeapFrame
import ml.combust.mleap.runtime.transformer.Transformer
import ml.combust.mleap.serving.domain.v1.{LoadModelRequest, LoadModelResponse, UnloadModelRequest, UnloadModelResponse}
import ml.combust.mleap.runtime.MleapSupport._
import resource._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
  * Created by hollinwilkins on 1/30/17.
  */
class MleapService()
                  (implicit ec: ExecutionContext) {
  private var bundle_6323: Option[Bundle[Transformer]] = None
  private var bundle_4894: Option[Bundle[Transformer]] = None
  private var bundle_6317: Option[Bundle[Transformer]] = None
  private var bundle_6757: Option[Bundle[Transformer]] = None

  def setBundle(bundle: Bundle[Transformer], company_id: Int): Unit = synchronized {
      company_id match {
          case 6323 => this.bundle_6323 = Some(bundle)
          case 4894 => this.bundle_4894 = Some(bundle)
          case 6317 => this.bundle_6317 = Some(bundle)
          case 6757 => this.bundle_6757 = Some(bundle)
          case _ => Failure(new IllegalArgumentException("invalid company_id"))
      }
  }
  def unsetBundle(company_id: Int): Unit = synchronized {
      company_id match {
          case 6323 => this.bundle_6323 = None
          case 4894 => this.bundle_4894 = None
          case 6317 => this.bundle_6317 = None
          case 6757 => this.bundle_6757 = None
          case _ => Failure(new IllegalArgumentException("invalid company_id"))
      }
  }

  def loadModel(request: LoadModelRequest, company_id: Int): Future[LoadModelResponse] = Future {
    (for(bf <- managed(BundleFile(new File(request.path.get.toString)))) yield {
      bf.loadMleapBundle()
    }).tried.flatMap(identity)
  }.flatMap(r => Future.fromTry(r)).andThen {
    case Success(b) => setBundle(b, company_id)
  }.map(_ => LoadModelResponse())

  def unloadModel(request: UnloadModelRequest, company_id: Int): Future[UnloadModelResponse] = {
    unsetBundle(company_id)
    Future.successful(UnloadModelResponse())
  }

  def transform(frame: DefaultLeapFrame, company_id: Int): Try[DefaultLeapFrame] = synchronized {
      company_id match {
          case 6323 => bundle_6323.map {
                           _.root.transform(frame)
                       }.getOrElse(Failure(new IllegalStateException("no transformer loaded")))
          case 4894 => bundle_4894.map {
                           _.root.transform(frame)
                       }.getOrElse(Failure(new IllegalStateException("no transformer loaded")))
          case 6317 => bundle_6317.map {
                           _.root.transform(frame)
                       }.getOrElse(Failure(new IllegalStateException("no transformer loaded")))
          case 6757 => bundle_6757.map {
                           _.root.transform(frame)
                       }.getOrElse(Failure(new IllegalStateException("no transformer loaded")))
          case _ => Failure(new IllegalArgumentException("invalid company_id"))
      }
  }

  def getSchema(company_id: Int): Try[StructType] = synchronized {
      company_id match {
          case 6323 => bundle_6323.map {
                           bundle => Success(bundle.root.schema)
                       }.getOrElse(Failure(new IllegalStateException("no transformer loaded")))
          case 4894 => bundle_4894.map {
                           bundle => Success(bundle.root.schema)
                       }.getOrElse(Failure(new IllegalStateException("no transformer loaded")))
          case 6317 => bundle_6317.map {
                           bundle => Success(bundle.root.schema)
                       }.getOrElse(Failure(new IllegalStateException("no transformer loaded")))
          case 6757 => bundle_6757.map {
                           bundle => Success(bundle.root.schema)
                       }.getOrElse(Failure(new IllegalStateException("no transformer loaded")))
          case _ => Failure(new IllegalArgumentException("invalid company_id"))
      }
  }

}

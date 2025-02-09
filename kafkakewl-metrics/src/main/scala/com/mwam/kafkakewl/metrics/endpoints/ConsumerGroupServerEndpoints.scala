/*
 * SPDX-FileCopyrightText: 2023 Marshall Wace <opensource@mwam.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.mwam.kafkakewl.metrics.endpoints

import com.mwam.kafkakewl.common.http.ErrorResponse
import com.mwam.kafkakewl.common.telemetry.zServerLogicWithTracing
import com.mwam.kafkakewl.metrics.domain.KafkaConsumerGroupInfo
import com.mwam.kafkakewl.metrics.services.KafkaConsumerGroupInfoCache
import sttp.model.StatusCode
import sttp.tapir.ztapir.*
import zio.*
import zio.telemetry.opentelemetry.tracing.Tracing

class ConsumerGroupServerEndpoints(consumerGroupEndpoints: ConsumerGroupEndpoints,
                                   consumerGroupInfoCache: KafkaConsumerGroupInfoCache,
                                   tracing: Tracing) {
  given Tracing = tracing

  val endpoints: List[ZServerEndpoint[Any, Any]] = List(
    consumerGroupEndpoints.getGroupsEndpoint.zServerLogicWithTracing(_ => getConsumerGroups),
    consumerGroupEndpoints.getGroupEndpoint.zServerLogicWithTracing(group => getConsumerGroup(group)),
  )

  private def getConsumerGroups: ZIO[Any, Unit, Seq[String]] = consumerGroupInfoCache.getConsumerGroups

  private def getConsumerGroup(consumerGroup: String): ZIO[Any, ErrorResponse, KafkaConsumerGroupInfo] = for {
    _ <- tracing.addEvent("reading consumer group info from cache")
    _ <- tracing.setAttribute("group", consumerGroup)
    consumerGroupInfo <- consumerGroupInfoCache.getConsumerGroupInfo(consumerGroup)
    _ <- tracing.addEvent(consumerGroupInfo match
      case Some(_) => "read consumer group info from cache"
      case None => "consumer group not found in cache"
    )
    withErrorType <- ZIO.getOrFailWith(ErrorResponse(s"consumer group $consumerGroup not found", StatusCode.NotFound))(consumerGroupInfo)
  } yield withErrorType
}

object ConsumerGroupServerEndpoints {
  val live: ZLayer[ConsumerGroupEndpoints & KafkaConsumerGroupInfoCache & Tracing, Nothing, ConsumerGroupServerEndpoints] =
    ZLayer.fromFunction(ConsumerGroupServerEndpoints(_, _, _))
}

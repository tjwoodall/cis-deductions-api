/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package v2.services

import models.errors.{RuleDeductionsDateRangeInvalidError, RuleDuplicatePeriodError, RuleUnalignedDeductionsPeriodError, SubmissionIdFormatError}
import shared.controllers.EndpointLogContext
import shared.models.domain.{Nino, TaxYear}
import shared.models.errors.{
  DownstreamErrorCode,
  DownstreamErrors,
  ErrorWrapper,
  InternalError,
  MtdError,
  NinoFormatError,
  NotFoundError,
  RuleIncorrectOrEmptyBodyError,
  RuleTaxYearNotSupportedError
}
import shared.models.outcomes.ResponseWrapper
import shared.utils.UnitSpec
import uk.gov.hmrc.http.HeaderCarrier
import v2.fixtures.AmendRequestFixtures._
import v2.mocks.connectors.MockAmendConnector
import v2.models.domain.SubmissionId
import v2.models.request.amend.AmendRequestData

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AmendServiceSpec extends UnitSpec with MockAmendConnector {

  "service" when {

    "a service call is successful" should {

      "return a mapped result" in new Test {

        MockAmendConnector
          .amendDeduction(requestData)
          .returns(Future.successful(Right(ResponseWrapper("resultId", ()))))

        await(service.amendDeductions(requestData)) shouldBe Right(ResponseWrapper("resultId", ()))
      }
    }
    "a service call is unsuccessful" should {

      def serviceError(downstreamErrorCode: String, error: MtdError): Unit =
        s"return a $downstreamErrorCode error is returned from the service" in new Test {

          MockAmendConnector
            .amendDeduction(requestData)
            .returns(Future.successful(Left(ResponseWrapper("resultId", DownstreamErrors.single(DownstreamErrorCode(downstreamErrorCode))))))

          await(service.amendDeductions(requestData)) shouldBe Left(ErrorWrapper("resultId", error))
        }

      val errors = List(
        "INVALID_TAXABLE_ENTITY_ID" -> NinoFormatError,
        "INVALID_PAYLOAD"           -> RuleIncorrectOrEmptyBodyError,
        "INVALID_SUBMISSION_ID"     -> SubmissionIdFormatError,
        "INVALID_CORRELATIONID"     -> InternalError,
        "NO_DATA_FOUND"             -> NotFoundError,
        "INVALID_TAX_YEAR_ALIGN"    -> RuleUnalignedDeductionsPeriodError,
        "INVALID_DATE_RANGE"        -> RuleDeductionsDateRangeInvalidError,
        "DUPLICATE_MONTH"           -> RuleDuplicatePeriodError,
        "SERVICE_UNAVAILABLE"       -> InternalError,
        "SERVICE_ERROR"             -> InternalError
      )
      val extraTysErrors = List(
        "INVALID_TAX_YEAR"       -> InternalError,
        "INVALID_CORRELATION_ID" -> InternalError,
        "TAX_YEAR_NOT_SUPPORTED" -> RuleTaxYearNotSupportedError
      )

      (errors ++ extraTysErrors).foreach(args => (serviceError _).tupled(args))
    }
  }

  trait Test {

    private val nino         = Nino("AA123456A")
    private val submissionId = SubmissionId("S4636A77V5KB8625U")
    private val taxYear      = TaxYear.fromIso("2019-07-05")

    implicit val correlationId: String = "X-123"

    val requestData: AmendRequestData = AmendRequestData(nino, submissionId, taxYear, amendRequestObj)

    implicit val hc: HeaderCarrier              = HeaderCarrier()
    implicit val logContext: EndpointLogContext = EndpointLogContext("c", "ep")

    val service = new AmendService(connector = mockAmendConnector)

  }

}

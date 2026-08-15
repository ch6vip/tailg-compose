package com.tailg.plus.data.cloud

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit endpoint contract — one suspend method per official cloud endpoint,
 * 1:1 with the HTTP calls made by `lib/services/official_cloud_service.dart`
 * (paths resolved against `https://www.tailgdd.com/v1/api/`).
 *
 * Every method returns the raw envelope as `Map<String, Any?>` so the lenient
 * Dart parsing semantics (`code` / `msg` / `data` + unknown top-level keys)
 * survive untouched; Moshi's built-in Map/Object adapters handle the wire JSON
 * (no codegen needed for the dynamic envelope).
 *
 * The generic transport ([OfficialCloudApiClient.request]) reproduces the Dart
 * retry / redaction / summary behavior on top of OkHttp; this interface is the
 * typed, DI-ready contract and is constructed by the client.
 */
interface OfficialCloudApiService {

    /** `POST app/getCode?phone=...` — request SMS code. */
    @POST("app/getCode")
    suspend fun getCode(@Query("phone") phone: String): Response<Map<String, Any?>>

    /** `POST app/login` — SMS login; token comes back in the `Authorization` header. */
    @POST("app/login")
    suspend fun login(@Body body: Map<String, Any?>): Response<Map<String, Any?>>

    /** `POST app/getUserProfile`. */
    @POST("app/getUserProfile")
    suspend fun getUserProfile(@Header("Authorization") token: String?): Response<Map<String, Any?>>

    /** `POST app/updateUserProfile` — nickname / profile update. */
    @POST("app/updateUserProfile")
    suspend fun updateUserProfile(
        @Header("Authorization") token: String?,
        @Body body: Map<String, Any?>,
    ): Response<Map<String, Any?>>

    /** `POST app/userCarPage` — GarageV2 paged vehicle list. */
    @POST("app/userCarPage")
    suspend fun userCarPage(
        @Header("Authorization") token: String?,
        @Body body: Map<String, Any?>,
    ): Response<Map<String, Any?>>

    /** `POST app/centralControl/carStatus` — full vehicle list with BLE/MQTT credentials. */
    @POST("app/centralControl/carStatus")
    suspend fun carStatus(
        @Header("Authorization") token: String?,
        @Body body: Map<String, Any?>,
    ): Response<Map<String, Any?>>

    /** `POST app/msg/pageOfCarMsg`. */
    @POST("app/msg/pageOfCarMsg")
    suspend fun pageOfCarMsg(
        @Header("Authorization") token: String?,
        @Body body: Map<String, Any?>,
    ): Response<Map<String, Any?>>

    /** `POST app/msg/pageOfSysMsg`. */
    @POST("app/msg/pageOfSysMsg")
    suspend fun pageOfSysMsg(
        @Header("Authorization") token: String?,
        @Body body: Map<String, Any?>,
    ): Response<Map<String, Any?>>

    /** `POST app/centralControl/changeUsingCar` — switch current car. */
    @POST("app/centralControl/changeUsingCar")
    suspend fun changeUsingCar(
        @Header("Authorization") token: String?,
        @Body body: Map<String, Any?>,
    ): Response<Map<String, Any?>>

    /** `POST app/mine/batteryInfo`. */
    @POST("app/mine/batteryInfo")
    suspend fun batteryInfo(@Header("Authorization") token: String?): Response<Map<String, Any?>>

    /** `POST app/mine/bmsBatteryInfo` — `{uid, imei}`. */
    @POST("app/mine/bmsBatteryInfo")
    suspend fun bmsBatteryInfo(
        @Header("Authorization") token: String?,
        @Body body: Map<String, Any?>,
    ): Response<Map<String, Any?>>

    /** `POST app/centralControl/batteryType/ext`. */
    @POST("app/centralControl/batteryType/ext")
    suspend fun batteryTypeExt(@Header("Authorization") token: String?): Response<Map<String, Any?>>

    /** `POST app/centralControl/batteryType` — non-ext fallback catalog. */
    @POST("app/centralControl/batteryType")
    suspend fun batteryType(@Header("Authorization") token: String?): Response<Map<String, Any?>>

    /** `POST app/centralControl/batterySpecByType` — `{typeId}`. */
    @POST("app/centralControl/batterySpecByType")
    suspend fun batterySpecByType(
        @Header("Authorization") token: String?,
        @Body body: Map<String, Any?>,
    ): Response<Map<String, Any?>>

    /** `POST app/centralControl/batterySetUp` — affirm/correct battery. */
    @POST("app/centralControl/batterySetUp")
    suspend fun batterySetUp(
        @Header("Authorization") token: String?,
        @Body body: Map<String, Any?>,
    ): Response<Map<String, Any?>>

    /** `POST app/car/extend/getByCarId` — parking location by `carId`. */
    @POST("app/car/extend/getByCarId")
    suspend fun getByCarId(
        @Header("Authorization") token: String?,
        @Body body: Map<String, Any?>,
    ): Response<Map<String, Any?>>

    /** `POST app/device/getFenceData`. */
    @POST("app/device/getFenceData")
    suspend fun getFenceData(
        @Header("Authorization") token: String?,
        @Body body: Map<String, Any?>,
    ): Response<Map<String, Any?>>

    /** `POST app/device/updFenceData`. */
    @POST("app/device/updFenceData")
    suspend fun updFenceData(
        @Header("Authorization") token: String?,
        @Body body: Map<String, Any?>,
    ): Response<Map<String, Any?>>

    /** `POST app/car/updateCarInfo` — rename vehicle (`{carId, carNickName}`). */
    @POST("app/car/updateCarInfo")
    suspend fun updateCarInfo(
        @Header("Authorization") token: String?,
        @Body body: Map<String, Any?>,
    ): Response<Map<String, Any?>>

    /** `POST app/msg/getMessageControl`. */
    @POST("app/msg/getMessageControl")
    suspend fun getMessageControl(@Header("Authorization") token: String?): Response<Map<String, Any?>>

    /** `POST app/msg/setMessagePushConfig`. */
    @POST("app/msg/setMessagePushConfig")
    suspend fun setMessagePushConfig(
        @Header("Authorization") token: String?,
        @Body body: Map<String, Any?>,
    ): Response<Map<String, Any?>>

    /** `POST app/msg/delMsg` — clear all messages. */
    @POST("app/msg/delMsg")
    suspend fun delMsg(@Header("Authorization") token: String?): Response<Map<String, Any?>>

    /** `POST app/carTravel/records` — today ride mileage (`{frame, uid}`). */
    @POST("app/carTravel/records")
    suspend fun carTravelRecords(
        @Header("Authorization") token: String?,
        @Body body: Map<String, Any?>,
    ): Response<Map<String, Any?>>

    /** `POST app/appRiding/getRidingDetail` — ride statistics. */
    @POST("app/appRiding/getRidingDetail")
    suspend fun getRidingDetail(
        @Header("Authorization") token: String?,
        @Body body: Map<String, Any?>,
    ): Response<Map<String, Any?>>

    /** `POST app/centralControl/deviceTravel` — month travel history. */
    @POST("app/centralControl/deviceTravel")
    suspend fun deviceTravel(
        @Header("Authorization") token: String?,
        @Body body: Map<String, Any?>,
    ): Response<Map<String, Any?>>

    /** `POST app/centralControl/deviceTravelDetail` — travel track points. */
    @POST("app/centralControl/deviceTravelDetail")
    suspend fun deviceTravelDetail(
        @Header("Authorization") token: String?,
        @Body body: Map<String, Any?>,
    ): Response<Map<String, Any?>>

    /** `POST app/device/cmd/status` — cloud self check. */
    @POST("app/device/cmd/status")
    suspend fun deviceCmdStatus(
        @Header("Authorization") token: String?,
        @Body body: Map<String, Any?>,
    ): Response<Map<String, Any?>>

    /** `POST app/device/cmd/{command}` — lock/unlock/start/stop/search/openCushion. */
    @POST("app/device/cmd/{command}")
    suspend fun deviceCmd(
        @Header("Authorization") token: String?,
        @Path("command") command: String,
        @Body body: Map<String, Any?>,
    ): Response<Map<String, Any?>>

    /** `POST app/sim/queryDetail` — smart service / SIM status. */
    @POST("app/sim/queryDetail")
    suspend fun queryDetail(
        @Header("Authorization") token: String?,
        @Body body: Map<String, Any?>,
    ): Response<Map<String, Any?>>

    /** `POST app/car/setCarOperator`. */
    @POST("app/car/setCarOperator")
    suspend fun setCarOperator(
        @Header("Authorization") token: String?,
        @Body body: Map<String, Any?>,
    ): Response<Map<String, Any?>>

    /** `POST app/web/hid/on` / `app/web/hid/off` — KKS induction switch. */
    @POST("app/web/hid/{action}")
    suspend fun webHid(
        @Header("Authorization") token: String?,
        @Path("action") action: String,
        @Body body: Map<String, Any?>,
    ): Response<Map<String, Any?>>

    /** `POST app/car/bikeBind` — bind vehicle by IMEI. */
    @POST("app/car/bikeBind")
    suspend fun bikeBind(
        @Header("Authorization") token: String?,
        @Body body: Map<String, Any?>,
    ): Response<Map<String, Any?>>

    /** `POST app/car/bikeUnbind` — unbind vehicle. */
    @POST("app/car/bikeUnbind")
    suspend fun bikeUnbind(
        @Header("Authorization") token: String?,
        @Body body: Map<String, Any?>,
    ): Response<Map<String, Any?>>

    /** `POST app/firmVersionInfo/getFirmVersion` — OTA firmware query. */
    @POST("app/firmVersionInfo/getFirmVersion")
    suspend fun getFirmVersion(
        @Header("Authorization") token: String?,
        @Body body: Map<String, Any?>,
    ): Response<Map<String, Any?>>
}

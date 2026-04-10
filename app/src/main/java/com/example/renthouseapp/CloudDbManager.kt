package com.example.renthouseapp

import android.content.Context
import android.util.Log
import android.os.Build
import com.huawei.agconnect.auth.AGConnectAuth
import com.huawei.agconnect.cloud.database.AGConnectCloudDB
import com.huawei.agconnect.cloud.database.CloudDBZone
import com.huawei.agconnect.cloud.database.CloudDBZoneConfig
import com.huawei.agconnect.cloud.database.CloudDBZoneQuery
import com.huawei.agconnect.cloud.database.exceptions.AGConnectCloudDBException
import java.lang.reflect.Modifier

class CloudDbManager(private val context: Context) {

    companion object {
        private const val TAG = "CloudDbManager"
        private const val ZONE_NAME = "RentHouseZone"
    }

    private val cloudDB by lazy { AGConnectCloudDB.getInstance() }
    private var zone: CloudDBZone? = null


    private fun isCloudDbAbiSupported(): Boolean {
        return Build.SUPPORTED_ABIS.any { abi -> abi.startsWith("arm") }
    }


    private fun ensureCloudDbNativeLoaded() {
        try {
            System.loadLibrary("naturalbase_cloud_jni")
        } catch (e: UnsatisfiedLinkError) {
            // 某些设备/ROM自动加载失败时，手动加载一次native库。
            Log.w(TAG, "manual load naturalbase_cloud_jni failed", e)
            throw e
        }
    }

    fun init(
        onSuccess: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        if (!isCloudDbAbiSupported()) {
            val message = "Cloud DB native库当前仅支持ARM ABI，请在ARM真机/ARM模拟器运行。当前ABI=${Build.SUPPORTED_ABIS.joinToString()}"
            Log.e(TAG, message)
            onError(IllegalStateException(message))
            return
        }

        try {
            AGConnectCloudDB.initialize(context)
            ensureCloudDbNativeLoaded()
            cloudDB.createObjectType(ObjectTypeInfoHelper.getObjectTypeInfo())
        } catch (t: Throwable) {
            Log.e(TAG, "Cloud DB init failed", t)
            onError(t)
            return
        }

        AGConnectAuth.getInstance().signInAnonymously()
            .addOnSuccessListener {
                openZone(onSuccess, onError)
            }
            .addOnFailureListener { e ->
                onError(e)
            }
    }

    private fun openZone(
        onSuccess: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val config = CloudDBZoneConfig(
            ZONE_NAME,
            CloudDBZoneConfig.CloudDBZoneSyncProperty.CLOUDDBZONE_CLOUD_CACHE,
            CloudDBZoneConfig.CloudDBZoneAccessProperty.CLOUDDBZONE_PUBLIC
        ).apply {
            persistenceEnabled = true
        }

        cloudDB.openCloudDBZone2(config, true)
            .addOnSuccessListener { openedZone ->
                zone = openedZone
                onSuccess()
            }
            .addOnFailureListener { e ->
                onError(e)
            }
    }

    fun insertOrUpdateProperty(
        property: Property,
        onSuccess: (Int) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val dbZone = zone ?: run {
            onError(IllegalStateException("Cloud DB zone not opened"))
            return
        }

        dbZone.executeUpsert(property)
            .addOnSuccessListener { count ->
                onSuccess(count)
            }
            .addOnFailureListener { e ->
                onError(e)
            }
    }

    fun queryAllProperties(
        onSuccess: (List<Property>) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val dbZone = zone ?: run {
            onError(IllegalStateException("Cloud DB zone not opened"))
            return
        }

        val query = CloudDBZoneQuery.where(Property::class.java)

        dbZone.executeQuery(
            query,
            CloudDBZoneQuery.CloudDBZoneQueryPolicy.POLICY_QUERY_FROM_CLOUD_ONLY
        ).addOnSuccessListener { snapshot ->
            try {
                val list = mutableListOf<Property>()
                val cursor = snapshot.snapshotObjects
                while (cursor.hasNext()) {
                    cursor.next()?.let { list.add(it) }
                }
                onSuccess(list)
            } catch (e: Exception) {
                onError(e)
            } finally {
                snapshot.release()
            }
        }.addOnFailureListener { e ->
            onError(e)
        }
    }

    fun insertOrUpdateRentalRecord(
        record: RentalRecord,
        onSuccess: (Int) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val dbZone = zone ?: run {
            onError(IllegalStateException("Cloud DB zone not opened"))
            return
        }

        dbZone.executeUpsert(record)
            .addOnSuccessListener { count -> onSuccess(count) }
            .addOnFailureListener { e -> onError(e) }
    }

    fun insertOrUpdateLoginUser(
        user: LoginUser,
        onSuccess: (Int) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val dbZone = zone ?: run {
            onError(IllegalStateException("Cloud DB zone not opened"))
            return
        }

        dbZone.executeUpsert(user)
            .addOnSuccessListener { count -> onSuccess(count) }
            .addOnFailureListener { e -> onError(e) }
    }

    fun queryLoginUserByPhoneNumber(
        phoneNumber: String,
        onSuccess: (LoginUser?) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val dbZone = zone ?: run {
            onError(IllegalStateException("Cloud DB zone not opened"))
            return
        }

        val query = CloudDBZoneQuery.where(LoginUser::class.java)
            .equalTo("phoneNumber", phoneNumber)
        dbZone.executeQuery(query, CloudDBZoneQuery.CloudDBZoneQueryPolicy.POLICY_QUERY_FROM_CLOUD_ONLY)
            .addOnSuccessListener { snapshot ->
                try {
                    val cursor = snapshot.snapshotObjects
                    onSuccess(if (cursor.hasNext()) cursor.next() else null)
                } catch (e: Exception) {
                    onError(e)
                } finally {
                    snapshot.release()
                }
            }
            .addOnFailureListener { e -> onError(e) }
    }

    fun queryAllLoginUsers(
        onSuccess: (List<LoginUser>) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val dbZone = zone ?: run {
            onError(IllegalStateException("Cloud DB zone not opened"))
            return
        }

        val query = CloudDBZoneQuery.where(LoginUser::class.java)
        dbZone.executeQuery(query, CloudDBZoneQuery.CloudDBZoneQueryPolicy.POLICY_QUERY_FROM_CLOUD_ONLY)
            .addOnSuccessListener { snapshot ->
                try {
                    val list = mutableListOf<LoginUser>()
                    val cursor = snapshot.snapshotObjects
                    while (cursor.hasNext()) {
                        cursor.next()?.let { list.add(it) }
                    }
                    onSuccess(list)
                } catch (e: Exception) {
                    onError(e)
                } finally {
                    snapshot.release()
                }
            }
            .addOnFailureListener { e -> onError(e) }
    }

    fun queryRentalRecordById(
        rentalId: String,
        onSuccess: (RentalRecord?) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val dbZone = zone ?: run {
            onError(IllegalStateException("Cloud DB zone not opened"))
            return
        }

        val query = CloudDBZoneQuery.where(RentalRecord::class.java).equalTo("id", rentalId)
        dbZone.executeQuery(query, CloudDBZoneQuery.CloudDBZoneQueryPolicy.POLICY_QUERY_FROM_CLOUD_ONLY)
            .addOnSuccessListener { snapshot ->
                try {
                    val cursor = snapshot.snapshotObjects
                    onSuccess(if (cursor.hasNext()) cursor.next() else null)
                } catch (e: Exception) {
                    onError(e)
                } finally {
                    snapshot.release()
                }
            }
            .addOnFailureListener { e -> onError(e) }
    }

    fun queryAllRentalRecords(
        onSuccess: (List<RentalRecord>) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val dbZone = zone ?: run {
            onError(IllegalStateException("Cloud DB zone not opened"))
            return
        }

        val query = CloudDBZoneQuery.where(RentalRecord::class.java)
        dbZone.executeQuery(query, CloudDBZoneQuery.CloudDBZoneQueryPolicy.POLICY_QUERY_FROM_CLOUD_ONLY)
            .addOnSuccessListener { snapshot ->
                try {
                    val list = mutableListOf<RentalRecord>()
                    val cursor = snapshot.snapshotObjects
                    while (cursor.hasNext()) {
                        cursor.next()?.let { list.add(it) }
                    }
                    onSuccess(list)
                } catch (e: Exception) {
                    onError(e)
                } finally {
                    snapshot.release()
                }
            }
            .addOnFailureListener { e -> onError(e) }
    }

    fun deleteRentalRecord(
        record: RentalRecord,
        onSuccess: (Int) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val dbZone = zone ?: run {
            onError(IllegalStateException("Cloud DB zone not opened"))
            return
        }

        dbZone.executeDelete(record)
            .addOnSuccessListener { count -> onSuccess(count) }
            .addOnFailureListener { e -> onError(e) }
    }

    fun insertOrUpdatePaymentRecord(
        record: PaymentRecord,
        onSuccess: (Int) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val dbZone = zone ?: run {
            onError(IllegalStateException("Cloud DB zone not opened"))
            return
        }

        dbZone.executeUpsert(record)
            .addOnSuccessListener { count -> onSuccess(count) }
            .addOnFailureListener { e -> onError(e) }
    }

    fun queryAllPaymentRecords(
        onSuccess: (List<PaymentRecord>) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val dbZone = zone ?: run {
            onError(IllegalStateException("Cloud DB zone not opened"))
            return
        }

        val query = CloudDBZoneQuery.where(PaymentRecord::class.java)
        dbZone.executeQuery(query, CloudDBZoneQuery.CloudDBZoneQueryPolicy.POLICY_QUERY_FROM_CLOUD_ONLY)
            .addOnSuccessListener { snapshot ->
                try {
                    val list = mutableListOf<PaymentRecord>()
                    val cursor = snapshot.snapshotObjects
                    while (cursor.hasNext()) {
                        cursor.next()?.let { list.add(it) }
                    }
                    onSuccess(list)
                } catch (e: Exception) {
                    onError(e)
                } finally {
                    snapshot.release()
                }
            }
            .addOnFailureListener { e -> onError(e) }
    }

    fun queryPaymentRecordsByRentalId(
        rentalId: String,
        onSuccess: (List<PaymentRecord>) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val dbZone = zone ?: run {
            onError(IllegalStateException("Cloud DB zone not opened"))
            return
        }

        val query = CloudDBZoneQuery.where(PaymentRecord::class.java).equalTo("rentalId", rentalId)
        dbZone.executeQuery(query, CloudDBZoneQuery.CloudDBZoneQueryPolicy.POLICY_QUERY_FROM_CLOUD_ONLY)
            .addOnSuccessListener { snapshot ->
                try {
                    val list = mutableListOf<PaymentRecord>()
                    val cursor = snapshot.snapshotObjects
                    while (cursor.hasNext()) {
                        cursor.next()?.let { list.add(it) }
                    }
                    onSuccess(list)
                } catch (e: Exception) {
                    onError(e)
                } finally {
                    snapshot.release()
                }
            }
            .addOnFailureListener { e -> onError(e) }
    }

    fun deletePaymentRecord(
        record: PaymentRecord,
        onSuccess: (Int) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val dbZone = zone ?: run {
            onError(IllegalStateException("Cloud DB zone not opened"))
            return
        }

        dbZone.executeDelete(record)
            .addOnSuccessListener { count -> onSuccess(count) }
            .addOnFailureListener { e -> onError(e) }
    }

    fun deleteProperty(
        property: Property,
        onSuccess: (Int) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val dbZone = zone ?: run {
            onError(IllegalStateException("Cloud DB zone not opened"))
            return
        }

        dbZone.executeDelete(property)
            .addOnSuccessListener { count -> onSuccess(count) }
            .addOnFailureListener { e -> onError(e) }
    }

    fun close() {
        val dbZone = zone ?: return
        try {
            val staticCloseMethod = AGConnectCloudDB::class.java.methods.firstOrNull { method ->
                method.name == "closeCloudDBZone" &&
                    Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.contentEquals(arrayOf(CloudDBZone::class.java))
            }
            if (staticCloseMethod != null) {
                staticCloseMethod.invoke(null, dbZone)
            } else {
                cloudDB.closeCloudDBZone(dbZone)
            }
            zone = null
        } catch (e: AGConnectCloudDBException) {
            Log.e(TAG, "close zone failed", e)
        } catch (e: Exception) {
            Log.e(TAG, "close zone failed", e)
        }
    }
}

package com.example.renthouseapp

import android.content.Context
import android.util.Log
import com.huawei.agconnect.auth.AGConnectAuth
import com.huawei.agconnect.cloud.database.AGConnectCloudDB
import com.huawei.agconnect.cloud.database.CloudDBZone
import com.huawei.agconnect.cloud.database.CloudDBZoneConfig
import com.huawei.agconnect.cloud.database.CloudDBZoneQuery
import com.huawei.agconnect.cloud.database.exceptions.AGConnectCloudDBException

class CloudDbManager(private val context: Context) {

    companion object {
        private const val TAG = "CloudDbManager"
        private const val ZONE_NAME = "RentHouseZone"
    }

    private val cloudDB by lazy { AGConnectCloudDB.getInstance() }
    private var zone: CloudDBZone? = null

    fun init(
        onSuccess: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        try {
            AGConnectCloudDB.initialize(context)
            cloudDB.createObjectType(ObjectTypeInfoHelper.getObjectTypeInfo())
        } catch (e: Exception) {
            onError(e)
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
        try {
            zone?.close()
        } catch (e: AGConnectCloudDBException) {
            Log.e(TAG, "close zone failed", e)
        }
    }
}

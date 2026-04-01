package com.example.renthouseapp

import android.content.Context
import android.util.Log
import com.huawei.agconnect.AGConnectInstance
import com.huawei.agconnect.cloud.database.AGConnectCloudDB
import com.huawei.agconnect.cloud.database.CloudDBZone
import com.huawei.agconnect.cloud.database.CloudDBZoneConfig

object CloudDBManager {
    private const val TAG = "CloudDBManager"
    private var mCloudDB: AGConnectCloudDB? = null
    var mCloudDBZone: CloudDBZone? = null
        private set

    fun init(context: Context, onInitSuccess: () -> Unit) {
        try {
            // 正确的华为 SDK 初始化顺序
            AGConnectInstance.initialize(context.applicationContext)
            AGConnectCloudDB.initialize(context.applicationContext)

            mCloudDB = AGConnectCloudDB.getInstance()
            mCloudDB?.createObjectType(ObjectTypeInfoHelper.getObjectTypeInfo())

            openZone(onInitSuccess)
        } catch (e: Exception) {
            Log.e(TAG, "初始化异常: ${e.message}")
        }
    }

    private fun openZone(onSuccess: () -> Unit) {
        val mConfig = CloudDBZoneConfig(
            "RentHouseZone",
            CloudDBZoneConfig.CloudDBZoneSyncProperty.CLOUDDBZONE_CLOUD_CACHE,
            CloudDBZoneConfig.CloudDBZoneAccessProperty.CLOUDDBZONE_PUBLIC
        )
        mConfig.persistenceEnabled = true

        mCloudDB?.openCloudDBZone2(mConfig, true)?.addOnSuccessListener {
            mCloudDBZone = it
            onSuccess()
        }?.addOnFailureListener {
            Log.e(TAG, "打开存储区失败: ${it.message}")
        }
    }
}
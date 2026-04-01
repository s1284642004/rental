package com.example.renthouseapp

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import com.example.renthouseapp.model.Rental as LocalRental // 本地 UI 用这个
import com.example.renthouseapp.Rental as CloudRental     // 华为云端用这个
import com.huawei.agconnect.cloud.database.CloudDBZoneQuery
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

class RentalViewModel : ViewModel() {
    private val _rentals = mutableStateListOf<LocalRental>()
    val rentals: List<LocalRental> get() = _rentals

    // 内部转换工具，确保日期不错乱
    private fun Date?.toLocalDate(): LocalDate =
        this?.toInstant()?.atZone(ZoneId.systemDefault())?.toLocalDate() ?: LocalDate.now()

    private fun LocalDate.toDate(): Date =
        Date.from(this.atStartOfDay(ZoneId.systemDefault()).toInstant())

    // 翻译官：把云端 Java 数据塞进你的 Kotlin 构造函数里
    private fun mapToLocal(c: CloudRental) = LocalRental(
        id = c.id ?: "",
        propertyName = c.propertyName ?: "",
        tenantName = c.tenantName ?: "",
        tenantPhone = c.tenantPhone ?: "",
        tenantIdCard = c.tenantIdCard ?: "",
        contractDate = c.contractDate.toLocalDate(),
        rentStartDate = c.rentStartDate.toLocalDate(),
        monthlyRent = c.monthlyRent ?: 0,
        leaseMonths = c.leaseMonths ?: 0,
        paymentFrequency = c.paymentFrequency ?: 0
    )

    private fun mapToCloud(l: LocalRental): CloudRental {
        val c = CloudRental()
        c.id = l.id
        c.propertyName = l.propertyName
        c.tenantName = l.tenantName
        c.tenantPhone = l.tenantPhone
        c.tenantIdCard = l.tenantIdCard
        c.contractDate = l.contractDate.toDate()
        c.rentStartDate = l.rentStartDate.toDate()
        c.monthlyRent = l.monthlyRent
        c.leaseMonths = l.leaseMonths
        c.paymentFrequency = l.paymentFrequency
        return c
    }

    // 获取数据并刷新 UI
    fun fetchRentals() {
        val zone = CloudDBManager.mCloudDBZone ?: return
        zone.executeQuery(
            CloudDBZoneQuery.where(CloudRental::class.java),
            CloudDBZoneQuery.CloudDBZoneQueryPolicy.POLICY_QUERY_FROM_CLOUD_ONLY
        ).addOnSuccessListener { snapshot ->
            _rentals.clear()
            val cursor = snapshot.snapshotObjects
            while (cursor.hasNext()) {
                _rentals.add(mapToLocal(cursor.next()))
            }
        }
    }

    fun addRental(rental: LocalRental) {
        val zone = CloudDBManager.mCloudDBZone ?: return
        zone.executeUpsert(mapToCloud(rental)).addOnSuccessListener {
            fetchRentals() // 保存后自动刷新
        }
    }
}
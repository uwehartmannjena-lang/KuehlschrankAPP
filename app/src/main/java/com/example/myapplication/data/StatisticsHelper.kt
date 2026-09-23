package com.example.myapplication.data

import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

enum class StatisticsTimeFrame(val title: String) {
    DIESER_MONAT("Dieser Monat"),
    LETZTE_30_TAGE("30 Tage"),
    DIESES_JAHR("Dieses Jahr"),
    GESAMT("Gesamt")
}

data class LocationStats(
    val location: String,
    val count: Int,
    val totalValue: Double
)

data class CategoryStats(
    val category: String,
    val count: Int,
    val totalValue: Double,
    val percentage: Double
)

data class CategoryWasteStats(
    val category: String,
    val count: Int,
    val totalLoss: Double,
    val totalCo2: Double
)

data class TopWastedItem(
    val name: String,
    val count: Int,
    val totalLoss: Double
)

data class TopConsumedItem(
    val name: String,
    val count: Int
)

data class NutritionStats(
    val veganPercentage: Double,
    val organicPercentage: Double,
    val totalKcal: Int,
    val averageProtein: Double
)

data class FreshnessOverview(
    val freshCount: Int,
    val expiringSoonCount: Int,
    val expiredCount: Int,
    val freshnessScore: Int // 0 - 100 %
)

data class SustainabilityStats(
    val consumedCount: Int,
    val wastedCount: Int,
    val rescueRate: Double, // 0.0 - 100.0 %
    val wastedValue: Double,
    val wastedCo2: Double
)

data class WasteTrendStats(
    val currentPeriodLoss: Double,
    val previousPeriodLoss: Double,
    val trendPercentage: Double, // negative means less waste (good), positive means more waste (bad)
    val isImproving: Boolean
)

data class CostProjection(
    val projectedWasteValue: Double,
    val isProjectionApplicable: Boolean
)

object StatisticsHelper {

    fun calculateCostProjection(wasted: List<WastedItem>, timeFrame: StatisticsTimeFrame, now: Long = System.currentTimeMillis()): CostProjection {
        if (timeFrame != StatisticsTimeFrame.DIESER_MONAT) return CostProjection(0.0, false)
        
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val currentDay = cal.get(Calendar.DAY_OF_MONTH)
        
        val currentCutoff = getCutoffTimestamp(timeFrame, now)
        val currentWasted = wasted.filter { it.wasteDate >= currentCutoff }
        val currentLoss = currentWasted.sumOf { it.price * it.quantity }
        
        if (currentLoss == 0.0) return CostProjection(0.0, false)
        
        val dailyLoss = currentLoss / currentDay
        val projectedLoss = dailyLoss * daysInMonth
        
        return CostProjection(
            projectedWasteValue = Math.round(projectedLoss * 100.0) / 100.0,
            isProjectionApplicable = true
        )
    }

    fun calculateWasteTrend(wasted: List<WastedItem>, timeFrame: StatisticsTimeFrame, now: Long = System.currentTimeMillis()): WasteTrendStats {
        val currentCutoff = getCutoffTimestamp(timeFrame, now)
        
        // Define previous period based on timeframe
        val previousCutoff = when (timeFrame) {
            StatisticsTimeFrame.DIESER_MONAT -> {
                val cal = Calendar.getInstance().apply { timeInMillis = now }
                cal.add(Calendar.MONTH, -1)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            StatisticsTimeFrame.LETZTE_30_TAGE -> {
                now - TimeUnit.DAYS.toMillis(60)
            }
            StatisticsTimeFrame.DIESES_JAHR -> {
                val cal = Calendar.getInstance().apply { timeInMillis = now }
                cal.add(Calendar.YEAR, -1)
                cal.set(Calendar.DAY_OF_YEAR, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            StatisticsTimeFrame.GESAMT -> 0L // No previous period
        }

        val currentWasted = if (currentCutoff == 0L) wasted else wasted.filter { it.wasteDate >= currentCutoff }
        
        val previousWasted = if (previousCutoff == 0L || currentCutoff == 0L) {
            emptyList()
        } else {
            wasted.filter { it.wasteDate in previousCutoff until currentCutoff }
        }

        val currentLoss = currentWasted.sumOf { it.price * it.quantity }
        val previousLoss = previousWasted.sumOf { it.price * it.quantity }

        val trend = if (previousLoss > 0.0) {
            ((currentLoss - previousLoss) / previousLoss) * 100.0
        } else if (currentLoss > 0.0) {
            100.0 // from 0 to something
        } else {
            0.0 // from 0 to 0
        }

        return WasteTrendStats(
            currentPeriodLoss = Math.round(currentLoss * 100.0) / 100.0,
            previousPeriodLoss = Math.round(previousLoss * 100.0) / 100.0,
            trendPercentage = Math.round(trend * 10.0) / 10.0,
            isImproving = trend <= 0.0
        )
    }

    fun filterWastedByTimeframe(
        items: List<WastedItem>,
        timeFrame: StatisticsTimeFrame,
        now: Long = System.currentTimeMillis()
    ): List<WastedItem> {
        val cutoff = getCutoffTimestamp(timeFrame, now)
        return if (cutoff == 0L) items else items.filter { it.wasteDate >= cutoff }
    }

    fun filterConsumedByTimeframe(
        items: List<ConsumedItem>,
        timeFrame: StatisticsTimeFrame,
        now: Long = System.currentTimeMillis()
    ): List<ConsumedItem> {
        val cutoff = getCutoffTimestamp(timeFrame, now)
        return if (cutoff == 0L) items else items.filter { it.consumeDate >= cutoff }
    }

    fun getCutoffTimestamp(timeFrame: StatisticsTimeFrame, now: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        return when (timeFrame) {
            StatisticsTimeFrame.DIESER_MONAT -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            StatisticsTimeFrame.LETZTE_30_TAGE -> {
                now - TimeUnit.DAYS.toMillis(30)
            }
            StatisticsTimeFrame.DIESES_JAHR -> {
                cal.set(Calendar.DAY_OF_YEAR, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            StatisticsTimeFrame.GESAMT -> 0L
        }
    }

    fun calculateFreshnessOverview(items: List<FridgeItem>, now: Long = System.currentTimeMillis()): FreshnessOverview {
        if (items.isEmpty()) return FreshnessOverview(0, 0, 0, 100)

        var fresh = 0
        var expiringSoon = 0
        var expired = 0

        for (item in items) {
            val exp = item.expiryDate
            if (exp == null) {
                fresh++
            } else {
                val daysLeft = TimeUnit.MILLISECONDS.toDays(exp - now).toInt()
                when {
                    daysLeft < 0 -> expired++
                    daysLeft <= 3 -> expiringSoon++
                    else -> fresh++
                }
            }
        }

        val total = items.size
        // Frische-Score: Frische = 100%, Bald fällig = 50%, Abgelaufen = 0%
        val score = (((fresh * 1.0) + (expiringSoon * 0.5)) / total * 100.0).toInt().coerceIn(0, 100)

        return FreshnessOverview(
            freshCount = fresh,
            expiringSoonCount = expiringSoon,
            expiredCount = expired,
            freshnessScore = score
        )
    }

    fun calculateSustainabilityStats(
        consumed: List<ConsumedItem>,
        wasted: List<WastedItem>
    ): SustainabilityStats {
        val totalConsumed = consumed.sumOf { it.quantity }
        val totalWasted = wasted.sumOf { it.quantity }
        val totalHandled = totalConsumed + totalWasted

        val rescueRate = if (totalHandled > 0) {
            (totalConsumed.toDouble() / totalHandled.toDouble()) * 100.0
        } else {
            100.0
        }

        val wastedVal = wasted.sumOf { it.price * it.quantity }
        val wastedCo2 = wasted.sumOf { it.carbonFootprint }

        return SustainabilityStats(
            consumedCount = totalConsumed,
            wastedCount = totalWasted,
            rescueRate = Math.round(rescueRate * 10.0) / 10.0,
            wastedValue = Math.round(wastedVal * 100.0) / 100.0,
            wastedCo2 = Math.round(wastedCo2 * 10.0) / 10.0
        )
    }

    fun calculateLocationStats(items: List<FridgeItem>): List<LocationStats> {
        val locations = listOf("Kühlschrank", "Gefrierfach", "Vorratskammer")
        val map = items.groupBy { it.storageLocation }

        return locations.map { loc ->
            val list = map[loc] ?: emptyList()
            LocationStats(
                location = loc,
                count = list.sumOf { it.quantity },
                totalValue = Math.round(list.sumOf { it.price * it.quantity } * 100.0) / 100.0
            )
        }
    }

    fun calculateCategoryStats(items: List<FridgeItem>): List<CategoryStats> {
        if (items.isEmpty()) return emptyList()
        val totalValue = items.sumOf { it.price * it.quantity }

        return items.groupBy { it.category }
            .map { (cat, list) ->
                val count = list.sumOf { it.quantity }
                val catValue = list.sumOf { it.price * it.quantity }
                val pct = if (totalValue > 0.0) (catValue / totalValue) * 100.0 else 0.0
                CategoryStats(
                    category = cat,
                    count = count,
                    totalValue = Math.round(catValue * 100.0) / 100.0,
                    percentage = Math.round(pct * 10.0) / 10.0
                )
            }
            .sortedByDescending { it.totalValue }
    }

    fun calculateWasteByCategory(wasted: List<WastedItem>): List<CategoryWasteStats> {
        return wasted.groupBy { it.category }
            .map { (cat, list) ->
                CategoryWasteStats(
                    category = cat,
                    count = list.sumOf { it.quantity },
                    totalLoss = Math.round(list.sumOf { it.price * it.quantity } * 100.0) / 100.0,
                    totalCo2 = Math.round(list.sumOf { it.carbonFootprint } * 10.0) / 10.0
                )
            }
            .sortedByDescending { it.totalLoss }
    }

    fun calculateTopWasted(wasted: List<WastedItem>, limit: Int = 5): List<TopWastedItem> {
        return wasted.groupBy { it.name.trim().lowercase() }
            .map { (_, list) ->
                val displayName = list.first().name
                TopWastedItem(
                    name = displayName,
                    count = list.sumOf { it.quantity },
                    totalLoss = Math.round(list.sumOf { it.price * it.quantity } * 100.0) / 100.0
                )
            }
            .sortedByDescending { it.count }
            .take(limit)
    }

    fun calculateTopConsumed(consumed: List<ConsumedItem>, limit: Int = 5): List<TopConsumedItem> {
        return consumed.groupBy { it.name.trim().lowercase() }
            .map { (_, list) ->
                val displayName = list.first().name
                TopConsumedItem(
                    name = displayName,
                    count = list.sumOf { it.quantity }
                )
            }
            .sortedByDescending { it.count }
            .take(limit)
    }

    fun calculateNutritionStats(items: List<FridgeItem>): NutritionStats {
        if (items.isEmpty()) return NutritionStats(0.0, 0.0, 0, 0.0)
        
        val totalItems = items.sumOf { it.quantity }
        if (totalItems == 0) return NutritionStats(0.0, 0.0, 0, 0.0)

        val veganCount = items.filter { it.isVegan }.sumOf { it.quantity }
        val organicCount = items.filter { it.isOrganic }.sumOf { it.quantity }
        val totalKcal = items.sumOf { it.kcal * it.quantity }
        val totalProtein = items.sumOf { it.protein * it.quantity }

        return NutritionStats(
            veganPercentage = Math.round((veganCount.toDouble() / totalItems) * 1000.0) / 10.0,
            organicPercentage = Math.round((organicCount.toDouble() / totalItems) * 1000.0) / 10.0,
            totalKcal = totalKcal,
            averageProtein = Math.round((totalProtein / totalItems) * 10.0) / 10.0
        )
    }

    fun generateShareReport(
        timeFrameTitle: String,
        inventoryCount: Int,
        inventoryValue: Double,
        consumedCount: Int,
        wastedCount: Int,
        wastedValue: Double,
        wastedCo2: Double,
        freshnessScore: Int,
        nutritionStats: NutritionStats? = null,
        wasteTrend: WasteTrendStats? = null,
        costProjection: CostProjection? = null,
        topConsumedItems: List<TopConsumedItem> = emptyList(),
        topWastedItems: List<TopWastedItem> = emptyList()
    ): String {
        val df = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)
        val today = df.format(Date())

        return buildString {
            appendLine("🍏 Kühlschrank Profi – Statistikbericht ($timeFrameTitle)")
            appendLine("Stand: $today")
            appendLine("────────────────────────────")
            appendLine("📦 Aktiver Vorrat: $inventoryCount Artikel (${String.format(Locale.GERMANY, "%.2f €", inventoryValue)})")
            appendLine("🌟 Frische-Index: $freshnessScore %")
            
            if (nutritionStats != null) {
                appendLine("🥗 Ernährung: ${nutritionStats.totalKcal} kcal, Ø ${nutritionStats.averageProtein}g Protein, ${nutritionStats.veganPercentage}% Vegan")
            }
            
            appendLine("────────────────────────────")
            appendLine("😋 Erfolgreich verbraucht: $consumedCount Artikel")
            if (topConsumedItems.isNotEmpty()) {
                appendLine("  🏆 Top-Lieblinge:")
                topConsumedItems.take(3).forEach { appendLine("  - ${it.name} (${it.count}x)") }
            }
            
            appendLine("🗑️ Im Müll gelandet: $wastedCount Artikel (${String.format(Locale.GERMANY, "%.2f €", wastedValue)})")
            
            if (wasteTrend != null && wasteTrend.previousPeriodLoss > 0.0) {
                val trendSymbol = if (wasteTrend.isImproving) "📉" else "📈"
                val trendSign = if (wasteTrend.trendPercentage > 0) "+" else ""
                appendLine("  $trendSymbol Trend: $trendSign${wasteTrend.trendPercentage}% zur Vorperiode")
            }
            
            if (costProjection != null && costProjection.isProjectionApplicable) {
                appendLine("  🔮 Prognose für diesen Monat: ~ ${String.format(Locale.GERMANY, "%.2f €", costProjection.projectedWasteValue)}")
            }

            if (topWastedItems.isNotEmpty()) {
                appendLine("  ⚠️ Größte Verluste:")
                topWastedItems.take(3).forEach { appendLine("  - ${it.name} (${it.count}x, ${String.format(Locale.GERMANY, "%.2f €", it.totalLoss)})") }
            }
            
            if (wastedCo2 > 0) {
                appendLine("🌍 CO2-Belastung durch Müll: ${String.format(Locale.GERMANY, "%.1f kg CO₂e", wastedCo2)}")
            }
            val total = consumedCount + wastedCount
            if (total > 0) {
                val rate = (consumedCount.toDouble() / total) * 100.0
                appendLine("♻️ Verwertungs-Quote: ${String.format(Locale.GERMANY, "%.1f %%", rate)}")
            }
            appendLine("────────────────────────────")
            appendLine("Gemeinsam gegen Lebensmittelverschwendung! 🌱")
        }
    }
}

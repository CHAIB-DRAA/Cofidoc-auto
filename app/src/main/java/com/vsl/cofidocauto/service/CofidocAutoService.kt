package com.vsl.cofidocauto.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.vsl.cofidocauto.model.Ride
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Package Cofidoc — à vérifier via adb shell "pm list packages | grep cofidoc"
const val COFIDOC_PACKAGE = "fr.cofidoc.mobile"
const val TAG = "CofidocAutoService"

class CofidocAutoService : AccessibilityService() {

    companion object {
        var rides: List<Ride> = emptyList()
        var currentRideIndex = 0
        var isRunning = false
        var step = 0

        // Callback pour notifier l'UI
        var onStatusUpdate: ((String) -> Unit)? = null
        var onFinished: (() -> Unit)? = null
    }

    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        info.notificationTimeout = 100
        serviceInfo = info
        Log.d(TAG, "Service connecté ✅")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isRunning) return
        val packageName = event?.packageName?.toString() ?: return
        if (packageName != COFIDOC_PACKAGE) return

        handler.postDelayed({
            processStep()
        }, 600)
    }

    private fun processStep() {
        if (currentRideIndex >= rides.size) {
            isRunning = false
            onStatusUpdate?.invoke("✅ Toutes les courses ont été traitées !")
            onFinished?.invoke()
            return
        }

        val ride = rides[currentRideIndex]
        val root = rootInActiveWindow ?: return

        when (step) {
            0 -> {
                // Cherche le bouton "Nouvelle facture" ou "+"
                onStatusUpdate?.invoke("Course ${currentRideIndex + 1}/${rides.size} : ${ride.patientName}")
                val newBtn = findNodeByText(root, "Nouvelle") 
                    ?: findNodeByText(root, "Ajouter")
                    ?: findNodeByContentDescription(root, "Ajouter")
                if (newBtn != null) {
                    newBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    step = 1
                    onStatusUpdate?.invoke("📋 Ouverture du formulaire...")
                } else {
                    onStatusUpdate?.invoke("⚠️ Bouton 'Nouvelle facture' introuvable. Ouvrez Cofidoc manuellement.")
                }
            }

            1 -> {
                // Remplir le nom du patient
                val nameField = findEditableFieldByHint(root, "Nom", "nom", "patient")
                if (nameField != null) {
                    fillField(nameField, ride.patientName)
                    step = 2
                    onStatusUpdate?.invoke("✏️ Nom renseigné : ${ride.patientName}")
                }
            }

            2 -> {
                // Remplir l'adresse de départ
                val addrField = findEditableFieldByHint(root, "adresse", "Adresse", "départ", "domicile")
                if (addrField != null) {
                    fillField(addrField, ride.startLocation)
                    step = 3
                    onStatusUpdate?.invoke("📍 Adresse départ : ${ride.startLocation}")
                }
            }

            3 -> {
                // Remplir la destination
                val destField = findEditableFieldByHint(root, "destination", "arrivée", "Destination")
                if (destField != null) {
                    fillField(destField, ride.endLocation)
                    step = 4
                    onStatusUpdate?.invoke("📍 Destination : ${ride.endLocation}")
                }
            }

            4 -> {
                // Remplir la distance
                val kmField = findEditableFieldByHint(root, "km", "kilomètre", "distance", "Distance")
                if (kmField != null) {
                    val distance = ride.realDistance?.toInt()?.toString() ?: "0"
                    fillField(kmField, distance)
                    step = 5
                    onStatusUpdate?.invoke("🚗 Distance : ${distance} km")
                }
            }

            5 -> {
                // Remplir la date
                val dateField = findEditableFieldByHint(root, "date", "Date", "JJ/MM")
                if (dateField != null) {
                    val formattedDate = formatDateForCofidoc(ride.date)
                    fillField(dateField, formattedDate)
                    step = 6
                    onStatusUpdate?.invoke("📅 Date : $formattedDate")
                }
            }

            6 -> {
                // Remplir les péages si > 0
                if (ride.tolls > 0) {
                    val tollField = findEditableFieldByHint(root, "péage", "Péage", "toll")
                    if (tollField != null) {
                        fillField(tollField, ride.tolls.toInt().toString())
                        onStatusUpdate?.invoke("💶 Péages : ${ride.tolls}€")
                    }
                }
                step = 7
                onStatusUpdate?.invoke("⏳ En attente de validation manuelle (sécu + naissance)...")
                // On s'arrête ici pour laisser l'utilisateur saisir le numéro de sécu et la date de naissance
                isRunning = false
                onStatusUpdate?.invoke(
                    "✋ Complétez : N° Sécu + Date de naissance\n" +
                    "Puis appuyez sur 'Course suivante' dans l'app."
                )
            }
        }
        root.recycle()
    }

    fun nextRide() {
        currentRideIndex++
        step = 0
        isRunning = true
    }

    // ─── Helpers ───────────────────────────────────────────────

    private fun fillField(node: AccessibilityNodeInfo, text: String) {
        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        node.performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT,
            Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        )
    }

    private fun findNodeByText(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val nodes = root.findAccessibilityNodeInfosByText(text)
        return nodes?.firstOrNull()
    }

    private fun findNodeByContentDescription(root: AccessibilityNodeInfo, desc: String): AccessibilityNodeInfo? {
        return findNodeRecursive(root) { node ->
            node.contentDescription?.contains(desc, ignoreCase = true) == true
        }
    }

    private fun findEditableFieldByHint(root: AccessibilityNodeInfo, vararg hints: String): AccessibilityNodeInfo? {
        return findNodeRecursive(root) { node ->
            node.isEditable && hints.any { hint ->
                node.hint?.contains(hint, ignoreCase = true) == true ||
                node.text?.contains(hint, ignoreCase = true) == true ||
                node.viewIdResourceName?.contains(hint, ignoreCase = true) == true
            }
        }
    }

    private fun findNodeRecursive(
        node: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (predicate(node)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findNodeRecursive(child, predicate)
            if (result != null) return result
            child.recycle()
        }
        return null
    }

    private fun formatDateForCofidoc(isoDate: String): String {
        return try {
            val inputFormats = listOf(
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.FRANCE),
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.FRANCE),
                SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE)
            )
            val outputFormat = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)
            var date: Date? = null
            for (fmt in inputFormats) {
                try { date = fmt.parse(isoDate); break } catch (_: Exception) {}
            }
            date?.let { outputFormat.format(it) } ?: isoDate
        } catch (e: Exception) {
            isoDate
        }
    }

    override fun onInterrupt() {
        isRunning = false
        Log.d(TAG, "Service interrompu")
    }
}

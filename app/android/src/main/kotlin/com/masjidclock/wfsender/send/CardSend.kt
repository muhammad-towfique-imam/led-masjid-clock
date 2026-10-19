package com.masjidclock.wfsender.send

import com.masjidclock.wfsender.protocol.CardSender
import com.masjidclock.wfsender.protocol.FailureReason
import com.masjidclock.wfsender.protocol.HdsExport
import com.masjidclock.wfsender.protocol.InvalidExportException
import com.masjidclock.wfsender.protocol.SafeSender
import com.masjidclock.wfsender.protocol.SendListener
import com.masjidclock.wfsender.protocol.SendResult
import com.masjidclock.wfsender.site.Card
import java.io.IOException

/** Loads the export of [card] and sends it. A missing or damaged export gives INVALID_EXPORT, not an exception. */
fun sendCard(card: Card, sender: CardSender, listener: SendListener): SendResult {
    val export = try {
        HdsExport.fromDirectory(card.dir)
    } catch (e: InvalidExportException) {
        return SendResult.Failed(FailureReason.INVALID_EXPORT, null, e.message ?: "invalid export")
    } catch (e: IOException) {
        return SendResult.Failed(FailureReason.INVALID_EXPORT, null, e.toString())
    }
    return sender.send(export, listener)
}

/** Like [sendCard], with the safe form (firmware V4.45). */
fun sendCardSafe(card: Card, sender: SafeSender, listener: SendListener): SendResult {
    val export = try {
        HdsExport.fromDirectory(card.dir)
    } catch (e: InvalidExportException) {
        return SendResult.Failed(FailureReason.INVALID_EXPORT, null, e.message ?: "invalid export")
    } catch (e: IOException) {
        return SendResult.Failed(FailureReason.INVALID_EXPORT, null, e.toString())
    }
    return sender.send(export, listener)
}


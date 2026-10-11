package com.awbuilds.auraspend.domain.usecase

import com.awbuilds.auraspend.data.classification.TransactionClassifier
import com.awbuilds.auraspend.domain.model.ParsedBankMessage

class ClassifyMessageUseCase {
    /**
     * [sender] is the SMS address when the message came from the inbox, and null
     * for a pasted message. It selects the per-bank parser, so the manual path
     * classifies exactly the way the background pipeline does.
     */
    operator fun invoke(rawMessage: String, sender: String? = null): ParsedBankMessage {
        return TransactionClassifier.classify(rawMessage, sender)
    }
}

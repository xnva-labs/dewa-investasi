package com.xnvalabs.smarteyex

import android.app.Application
import com.xnvalabs.smarteyex.data.auth.AuthRepository
import com.xnvalabs.smarteyex.data.call.CallRepository
import com.xnvalabs.smarteyex.data.education.EducationRepository
import com.xnvalabs.smarteyex.data.emergency.EmergencyRepository
import com.xnvalabs.smarteyex.data.enterprise.EnterpriseRepository
import com.xnvalabs.smarteyex.data.memory.MemoryRepository
import com.xnvalabs.smarteyex.data.notifications.NotificationRepository
import com.xnvalabs.smarteyex.data.privacy.PrivacyRepository
import com.xnvalabs.smarteyex.data.reminder.ReminderRepository
import com.xnvalabs.smarteyex.data.companion.CompanionRepository
import com.xnvalabs.smarteyex.data.intelligence.UserModelRepository
import com.xnvalabs.smarteyex.data.voice.VoiceProfileRepository

/** Central startup boundary so every process entry initializes the same stores. */
class SmartEyeXApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PrivacyRepository.init(this)
        NotificationRepository.bind(this)
        MemoryRepository.init(this)
        ReminderRepository.init(this)
        AuthRepository.init(this)
        EmergencyRepository.init(this)
        CallRepository.init(this)
        EducationRepository.init(this)
        EnterpriseRepository.init(this)
        UserModelRepository.init(this)
        CompanionRepository.init(this)
        VoiceProfileRepository.init(this)
    }
}

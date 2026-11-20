package com.nextz.kanomtts.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nextz.kanomtts.feature.settings.FirstLaunchRecoveryStatus
import com.nextz.kanomtts.feature.settings.FirstLaunchRecoveryUiState

@Composable
fun FirstLaunchRecoveryScreen(
    state: FirstLaunchRecoveryUiState,
    onRequestPermission: () -> Unit,
    onRetry: () -> Unit,
    onContinueAfterFailure: () -> Unit,
    onContinueWithoutPermission: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (state.status) {
            FirstLaunchRecoveryStatus.CHECKING -> {
                CircularProgressIndicator()
                Spacer(Modifier.height(20.dp))
                Text("กำลังตรวจสอบข้อมูล Kanom…", style = MaterialTheme.typography.titleMedium)
            }
            FirstLaunchRecoveryStatus.PERMISSION_REQUIRED -> {
                Text("ยินดีต้อนรับกลับสู่ Kanom", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(12.dp))
                Text(
                    "ก่อนเริ่มใช้งาน Kanom ต้องได้รับอนุญาตให้เข้าถึงไฟล์ในเครื่อง เพื่อค้นหาโฟลเดอร์ Kanom และตรวจสอบข้อมูลสำรองเดิม หากพบไฟล์สำรอง แอปจะกู้คืนให้โดยอัตโนมัติ",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                Button(onClick = onRequestPermission) {
                    Text("อนุญาตให้ Kanom เข้าถึงไฟล์")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onContinueWithoutPermission) {
                    Text("ใช้งานแอปโดยไม่กู้คืนก่อน")
                }
            }
            FirstLaunchRecoveryStatus.RESTORING -> {
                CircularProgressIndicator()
                Spacer(Modifier.height(20.dp))
                Text("กำลังกู้คืนข้อมูลเดิม…", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "กรุณารอสักครู่ อย่าปิดแอประหว่างดำเนินการ",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
            FirstLaunchRecoveryStatus.FAILED -> {
                Text("กู้คืนข้อมูลอัตโนมัติไม่สำเร็จ", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                Text(
                    state.errorMessage.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                Button(onClick = onRetry) { Text("ลองกู้คืนอีกครั้ง") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onContinueAfterFailure) {
                    Text("เข้าสู่แอปก่อน")
                }
            }
            FirstLaunchRecoveryStatus.READY -> Unit
        }
    }
}

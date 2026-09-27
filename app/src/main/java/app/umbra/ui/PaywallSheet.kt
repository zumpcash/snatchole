package app.umbra.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import app.umbra.ui.theme.*
import app.umbra.wallet.WalletUiState

@Composable
fun PaywallSheet(
    walletState: WalletUiState,
    onPaySol: () -> Unit,
    onPayUsdc: () -> Unit,
    onRestore: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Black.copy(alpha = 0.7f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(BlackSurface)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {}
                .padding(horizontal = 24.dp)
                .padding(top = 20.dp, bottom = 40.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Handle
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .background(WhiteDim, RoundedCornerShape(2.dp)),
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "R E K L A M S I Z",
                color = Gold,
                fontSize = 11.sp,
                letterSpacing = 4.sp,
                fontWeight = FontWeight.Medium,
            )

            Text(
                text = "Yıllık yaklaşık\n$1",
                color = White,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 42.sp,
            )

            Text(
                text = "Reklamlar 1 yıl boyunca kaybolur.\nSadece kripto — banka yok.",
                color = WhiteMuted,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
            )

            Spacer(Modifier.height(4.dp))

            when (walletState) {
                is WalletUiState.Loading -> {
                    Text("İşleniyor…", color = Gold, fontSize = 14.sp)
                }
                is WalletUiState.Error -> {
                    Text(
                        text = walletState.message,
                        color = RedErr,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                    PayButton("SOL ile Öde (~\$1)", Gold, Black, onPaySol)
                    PayButton("USDC ile Öde", BlackSurface2, White, onPayUsdc, outlined = true)
                    RestoreText(onRestore)
                }
                is WalletUiState.Success -> {
                    Text("Premium aktif ✓", color = GreenOk, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    PayButton("Tamam", Gold, Black, onDismiss)
                }
                else -> {
                    PayButton("SOL ile Öde (~\$1)", Gold, Black, onPaySol)
                    PayButton("USDC ile Öde", BlackSurface2, White, onPayUsdc, outlined = true)
                    RestoreText(onRestore)
                }
            }
        }
    }
}

@Composable
private fun PayButton(
    text: String,
    background: Color,
    textColor: Color,
    onClick: () -> Unit,
    outlined: Boolean = false,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (outlined) Modifier.border(1.dp, WhiteDim, RoundedCornerShape(10.dp)).background(background)
                else Modifier.background(background)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
        )
    }
}

@Composable
private fun RestoreText(onRestore: () -> Unit) {
    Text(
        text = "Önceki ödemeyi geri yükle",
        color = WhiteMuted,
        fontSize = 13.sp,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onRestore,
        ),
    )
}

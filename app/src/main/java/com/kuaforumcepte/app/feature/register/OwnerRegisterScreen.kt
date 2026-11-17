package com.kuaforumcepte.app.feature.register

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.kuaforumcepte.app.core.designsystem.component.ConfirmDialog
import com.kuaforumcepte.app.core.designsystem.component.ConsentRow
import com.kuaforumcepte.app.core.designsystem.component.InfoBanner
import com.kuaforumcepte.app.core.designsystem.component.KcDropdown
import com.kuaforumcepte.app.core.designsystem.component.KcPrimaryButton
import com.kuaforumcepte.app.core.designsystem.component.KcTextField
import com.kuaforumcepte.app.core.designsystem.component.KcTopBar
import com.kuaforumcepte.app.core.designsystem.component.SkeletonCardList
import com.kuaforumcepte.app.core.designsystem.component.asString
import com.kuaforumcepte.app.core.designsystem.theme.Spacing
import com.kuaforumcepte.app.feature.media.ImagePreviewDialog
import com.kuaforumcepte.app.data.media.ImageKind
import com.kuaforumcepte.app.data.registration.DraftImage
import com.kuaforumcepte.app.data.registration.LegalDocument
import com.kuaforumcepte.app.data.registration.OwnerDraft
import com.kuaforumcepte.app.data.registration.SalonCreated
import com.kuaforumcepte.domain.model.SalonType
import com.kuaforumcepte.domain.phone.PhoneNumbers
import java.io.File

@Composable
fun OwnerRegisterScreen(
    onCreated: (SalonCreated) -> Unit,
    onOpenLegal: (LegalDocument) -> Unit,
    onExit: () -> Unit,
    viewModel: OwnerRegisterViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val err = ui.error?.asString()
    var confirmExit by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.created.collect(onCreated) }
    LaunchedEffect(err) { if (err != null) { snackbar.showSnackbar(err); viewModel.dismissError() } }
    val goBack: () -> Unit = { if (!viewModel.back()) { confirmExit = true } }
    BackHandler(onBack = goBack)

    Scaffold(
        topBar = { KcTopBar("Salon Kaydı", goBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            val d = ui.draft
            if (d != null) {
                Surface(tonalElevation = 3.dp) {
                    Column(Modifier.navigationBarsPadding().padding(Spacing.l)) {
                        ui.progressLabel?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall)
                            LinearProgressIndicator(progress = { ui.progress }, modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.s))
                        }
                        KcPrimaryButton(
                            text = if (ui.step == OwnerStep.REVIEW) "Salonumu Oluştur" else "Devam",
                            onClick = viewModel::next,
                            loading = ui.submitting,
                            enabled = ui.step != OwnerStep.REVIEW || (d.kvkkAccepted && d.termsAccepted),
                        )
                    }
                }
            }
        },
    ) { padding ->
        val draft = ui.draft
        if (ui.loading || draft == null) {
            SkeletonCardList(3, Modifier.padding(padding))
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            StepHeader(ui.step)
            AnimatedContent(targetState = ui.step, label = "ownerStep") { step ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.screen)) {
                    when (step) {
                        OwnerStep.PERSONAL -> PersonalStep(draft, ui, viewModel)
                        OwnerStep.VENUE -> VenueStep(draft, ui, viewModel)
                        OwnerStep.ADDRESS -> AddressStep(draft, ui, viewModel)
                        OwnerStep.MEDIA -> MediaStep(draft, ui, viewModel)
                        OwnerStep.REVIEW -> ReviewStep(draft, ui, viewModel, onOpenLegal)
                    }
                }
            }
        }
    }

    ui.pendingImage?.let { p ->
        ImagePreviewDialog(p, viewModel::onPendingMode, viewModel::confirmPending, viewModel::cancelPending)
    }

    if (confirmExit) {
        ConfirmDialog(
            title = "Kayıttan çıkılsın mı?",
            message = "Girdiğiniz bilgiler bu cihazda saklanır; daha sonra kaldığınız yerden devam edebilirsiniz.",
            confirmLabel = "Çık",
            onConfirm = { confirmExit = false; onExit() },
            onDismiss = { confirmExit = false },
        )
    }
}

@Composable
private fun StepHeader(step: OwnerStep) {
    val total = OwnerStep.entries.size
    Column(Modifier.padding(horizontal = Spacing.screen)) {
        Text("Adım ${step.ordinal + 1}/$total · ${step.title}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
        LinearProgressIndicator(
            progress = { (step.ordinal + 1f) / total },
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.s).semantics { contentDescription = "Adım ${step.ordinal + 1} / $total" },
        )
    }
}

@Composable
private fun PersonalStep(d: OwnerDraft, ui: OwnerRegisterUi, vm: OwnerRegisterViewModel) {
    Text("Salon sahibi", style = MaterialTheme.typography.headlineSmall)
    Text("Salon sahibi aynı zamanda randevu alınabilir bir uzman olarak listelenir.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(Spacing.l))
    KcTextField(d.firstName, vm::onFirstName, "Ad", error = ui.errors["firstName"], capitalization = KeyboardCapitalization.Words, maxLength = 50)
    Spacer(Modifier.height(Spacing.s))
    KcTextField(d.lastName, vm::onLastName, "Soyad", error = ui.errors["lastName"], capitalization = KeyboardCapitalization.Words, maxLength = 50)
    Spacer(Modifier.height(Spacing.s))
    KcTextField(PhoneNumbers.formatForDisplay(ui.phoneE164), {}, "Cep telefonu (doğrulandı)", enabled = false, supporting = "Bu numara herkese gösterilmez.")
}

@Composable
private fun VenueStep(d: OwnerDraft, ui: OwnerRegisterUi, vm: OwnerRegisterViewModel) {
    Text("Mekan bilgileri", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(Spacing.l))
    KcTextField(d.salonName, vm::onSalonName, "Mekan adı", error = ui.errors["salonName"], capitalization = KeyboardCapitalization.Words, maxLength = 80)
    Spacer(Modifier.height(Spacing.s))
    KcTextField(
        d.businessPhone, vm::onBusinessPhone, "Mekan telefonu", error = ui.errors["businessPhone"],
        keyboardType = KeyboardType.Phone, supporting = "Müşterilere salon bilgilerinde gösterilir.",
    )
    Spacer(Modifier.height(Spacing.s))
    KcDropdown(
        label = "Salon türü",
        options = SalonType.entries.toList(),
        selected = SalonType.fromWire(d.salonType),
        optionLabel = { it.label },
        onSelect = vm::onType,
        error = ui.errors["salonType"],
    )
}

@Composable
private fun AddressStep(d: OwnerDraft, ui: OwnerRegisterUi, vm: OwnerRegisterViewModel) {
    Text("Adres", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(Spacing.l))
    KcDropdown("İl", ui.cities, ui.cities.firstOrNull { it.name == d.city }, { it.name }, vm::onCity, error = ui.errors["city"])
    Spacer(Modifier.height(Spacing.s))
    KcDropdown("İlçe", ui.districts, d.district, { it }, vm::onDistrict, enabled = d.city != null, error = ui.errors["district"])
    Spacer(Modifier.height(Spacing.s))
    KcTextField(d.addressLine, vm::onAddress, "Açık adres", error = ui.errors["addressLine"], singleLine = false, minLines = 2, maxLength = 300)
    Spacer(Modifier.height(Spacing.s))
    KcTextField(
        d.description, vm::onDescription, "Salon açıklaması (isteğe bağlı)", error = ui.errors["description"],
        singleLine = false, minLines = 3, maxLength = 1000, imeAction = ImeAction.Default,
        supporting = "Daha sonra Dükkan Ayarları'ndan düzenleyebilirsiniz.",
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MediaStep(d: OwnerDraft, ui: OwnerRegisterUi, vm: OwnerRegisterViewModel) {
    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { vm.onImagePicked(it, ImageKind.LOGO) }
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { vm.onImagePicked(it, ImageKind.PHOTO) }
    }
    val imageOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)

    Text("Logo (zorunlu)", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(Spacing.s))
    Row(verticalAlignment = Alignment.CenterVertically) {
        ImageTile(d.logo, aspect = 1f, size = 112, label = "Logo seç", onPick = { logoPicker.launch(imageOnly) }, onRemove = vm::removeLogo)
        Spacer(Modifier.width(Spacing.l))
        Text("Kare olarak kaydedilir. JPG, PNG, WEBP veya HEIC.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    ui.errors["logo"]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    Spacer(Modifier.height(Spacing.xl))
    Text("Mekan fotoğrafları (${d.photos.size}/${OwnerRegisterViewModel.MAX_PHOTOS}, isteğe bağlı)", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(Spacing.s))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        d.photos.forEachIndexed { i, p ->
            ImageTile(p, aspect = 4f / 3f, size = 104, label = "Fotoğraf ${i + 1}", onPick = {}, onRemove = { vm.removePhoto(i) })
        }
        if (d.photos.size < OwnerRegisterViewModel.MAX_PHOTOS) {
            ImageTile(null, aspect = 4f / 3f, size = 104, label = "Fotoğraf ekle", onPick = { photoPicker.launch(imageOnly) }, onRemove = {})
        }
    }
    Spacer(Modifier.height(Spacing.l))
    InfoBanner("Görseller cihazınızda sıkıştırılır ve en-boy oranı korunarak kırpılır veya sığdırılır. Yükleme yarıda kalırsa tekrar denediğinizde kaldığı yerden devam eder.")
}

@Composable
private fun ImageTile(image: DraftImage?, aspect: Float, size: Int, label: String, onPick: () -> Unit, onRemove: () -> Unit) {
    Box(
        Modifier.width(size.dp).aspectRatio(aspect).clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
            .clickable(enabled = image == null, role = Role.Button, onClickLabel = label, onClick = onPick),
        contentAlignment = Alignment.Center,
    ) {
        if (image == null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Text(label, style = MaterialTheme.typography.labelSmall)
            }
        } else {
            AsyncImage(File(image.localPath), contentDescription = label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            IconButton(
                onClick = onRemove,
                modifier = Modifier.align(Alignment.TopEnd).size(36.dp).padding(4.dp).clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
            ) { Icon(Icons.Outlined.Close, contentDescription = "$label kaldır", modifier = Modifier.size(18.dp)) }
        }
    }
}

@Composable
private fun ReviewStep(d: OwnerDraft, ui: OwnerRegisterUi, vm: OwnerRegisterViewModel, onOpenLegal: (LegalDocument) -> Unit) {
    Text("Bilgilerinizi kontrol edin", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(Spacing.l))
    SummaryRow("Salon sahibi", "${d.firstName} ${d.lastName}") { vm.goTo(OwnerStep.PERSONAL) }
    SummaryRow("Mekan", "${d.salonName} · ${SalonType.fromWire(d.salonType)?.label.orEmpty()}") { vm.goTo(OwnerStep.VENUE) }
    SummaryRow("Mekan telefonu", PhoneNumbers.normalizeTr(d.businessPhone)?.let(PhoneNumbers::formatForDisplay) ?: d.businessPhone) { vm.goTo(OwnerStep.VENUE) }
    SummaryRow("Adres", "${d.addressLine}, ${d.district} / ${d.city}") { vm.goTo(OwnerStep.ADDRESS) }
    SummaryRow("Görseller", "Logo + ${d.photos.size} fotoğraf") { vm.goTo(OwnerStep.MEDIA) }
    Spacer(Modifier.height(Spacing.l))
    InfoBanner("Kayıt tamamlandığında salonunuza değiştirilemez, 6 haneli bir salon kodu verilir. Kod, personel eşleştirmesi ve salon kimliği içindir; gizli bir şifre değildir.")
    Spacer(Modifier.height(Spacing.m))
    ConsentRow(d.kvkkAccepted, vm::onKvkk, "Aydınlatma metnini okudum.", "Aydınlatma metnini oku") { onOpenLegal(LegalDocument.KVKK) }
    ConsentRow(d.termsAccepted, vm::onTerms, "Kullanım koşullarını kabul ediyorum.", "Kullanım koşullarını oku") { onOpenLegal(LegalDocument.TERMS) }
    if (ui.errors.containsKey("consent")) Text("Devam etmek için iki onay da gereklidir.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun SummaryRow(label: String, value: String, onEdit: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.s), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
        TextButton(onClick = onEdit) { Text("Düzenle") }
    }
}


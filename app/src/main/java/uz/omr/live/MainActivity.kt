package uz.omr.live

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

data class ScanState(
    val answers: List<String?> = emptyList(),
    val stable: Boolean = false,
    val note: String = "Kamerani javoblar jadvaliga to‘g‘rilang",
    val ocrText: String = ""
)

class MainActivity : ComponentActivity() {
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            permission.launch(Manifest.permission.CAMERA)
        setContent { OmrScreen() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OmrScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var keyText by remember { mutableStateOf("A,B,C,D,E") }
    var answerKey by remember { mutableStateOf<List<String>>(emptyList()) }
    var questionCount by remember { mutableIntStateOf(35) }
    var optionCount by remember { mutableIntStateOf(5) }
    var scan by remember { mutableStateOf(ScanState()) }
    var keyMessage by remember { mutableStateOf("Kalit yuklanmagan") }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() } }
                .onSuccess { raw ->
                    val parsed = raw.uppercase().split(Regex("[,;\\s]+")).filter { it in listOf("A","B","C","D","E","F","G","H","I","J") }
                    if (parsed.isNotEmpty()) {
                        answerKey = parsed
                        questionCount = parsed.size
                        optionCount = maxOf(4, minOf(10, parsed.maxOf { it[0] - 'A' + 1 }))
                        keyMessage = "${parsed.size} ta javob yuklandi"
                    } else keyMessage = "Kalit topilmadi. Masalan: A,B,C,D"
                }.onFailure { keyMessage = "Faylni o‘qib bo‘lmadi" }
        }
    }
    val result = remember { AtomicReference(ScanState()) }

    Scaffold(topBar = { TopAppBar(title = { Text("OMR Live Scanner") }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("1. Javob kaliti", style = MaterialTheme.typography.titleMedium)
                        Text(keyMessage, color = if (answerKey.isEmpty()) Color(0xFFB45309) else Color(0xFF15803D))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { picker.launch("text/*") }) { Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(6.dp)); Text("TXT / CSV yuklash") }
                            OutlinedButton(onClick = {
                                val parsed = keyText.uppercase().split(Regex("[,;\\s]+")).filter { it.matches(Regex("[A-J]")) }
                                answerKey = parsed
                                questionCount = parsed.size
                                keyMessage = if (parsed.isEmpty()) "Kalitni kiriting" else "${parsed.size} ta javob tayyor"
                            }) { Text("Matndan olish") }
                        }
                        OutlinedTextField(keyText, { keyText = it }, Modifier.fillMaxWidth(), label = { Text("Kalit (A,B,C,D...)") }, minLines = 2)
                        Text("Fayl formati: A,B,C,D,A... yoki har qatorda bittadan javob.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Card {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("2. Skaner sozlamalari", style = MaterialTheme.typography.titleMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Savollar: $questionCount")
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { questionCount = (questionCount - 1).coerceAtLeast(1) }) { Text("−") }
                            TextButton(onClick = { questionCount = (questionCount + 1).coerceAtMost(200) }) { Text("+") }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Variantlar: $optionCount")
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { optionCount = (optionCount - 1).coerceAtLeast(2) }) { Text("−") }
                            TextButton(onClick = { optionCount = (optionCount + 1).coerceAtMost(10) }) { Text("+") }
                        }
                    }
                }
            }
            item {
                Text("3. Kamera — varaqni markazdagi ramkaga joylang", style = MaterialTheme.typography.titleMedium)
                CameraScanner(
                    modifier = Modifier.fillMaxWidth().height(340.dp),
                    onScan = { state -> scan = state; result.set(state) },
                    questions = questionCount, options = optionCount
                )
                if (scan.ocrText.isNotBlank()) {
                    Text("OCR orqali o‘qilgan matn", style = MaterialTheme.typography.titleSmall)
                    Text(scan.ocrText.take(500), style = MaterialTheme.typography.bodySmall)
                }
                Text("Eslatma: ML Kit OCR kameradagi bosma/yozma matnni real vaqtda o‘qishga urinadi. Qo‘lda yozilgan harflar va qiyshaygan varaqda xatolar bo‘lishi mumkin; natijani tasdiqlash talab etiladi.", style = MaterialTheme.typography.bodySmall)
            }
            item {
                val detected = scan.answers
                val matched = detected.indices.count { i -> i < answerKey.size && detected[i] != null && detected[i] == answerKey[i] }
                val marked = detected.count { it != null }
                val wrong = detected.indices.count { i -> i < answerKey.size && detected[i] != null && detected[i] != answerKey[i] }
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Jonli natija", style = MaterialTheme.typography.titleLarge)
                        Text("$matched / ${answerKey.size} to‘g‘ri", style = MaterialTheme.typography.headlineMedium)
                        Text("Xato: $wrong   •   Belgilanmagan: ${(questionCount - marked).coerceAtLeast(0)}")
                        Text(if (answerKey.isEmpty()) "Avval javob kalitini yuklang" else scan.note)
                    }
                }
            }
            item { Text("Aniqlangan javoblar", style = MaterialTheme.typography.titleMedium) }
            items(scan.answers.chunked(5).flatMapIndexed { block, row -> row.mapIndexed { idx, value -> (block * 5 + idx + 1) to value } }) { (q, value) ->
                val correct = answerKey.getOrNull(q - 1)
                Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("$q-savol")
                    Text(value ?: "—")
                    Text(if (value == null) "Aniqlanmadi" else if (value == correct) "To‘g‘ri" else "Xato",
                        color = if (value == correct && value != null) Color(0xFF15803D) else Color(0xFFB91C1C))
                }
            }
        }
    }
}

@Composable
fun CameraScanner(modifier: Modifier, questions: Int, options: Int, onScan: (ScanState) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    AndroidView(modifier = modifier, factory = { ctx ->
        val view = PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
        val providerFuture = ProcessCameraProvider.getInstance(ctx)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
            val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            analysis.setAnalyzer(executor, OmrAnalyzer(questions, options, onScan))
            try {
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (_: Exception) { onScan(ScanState(note = "Kamerani ishga tushirib bo‘lmadi")) }
        }, ContextCompat.getMainExecutor(ctx))
        view
    })
}

class OmrAnalyzer(private val questions: Int, private val options: Int, private val callback: (ScanState) -> Unit) : ImageAnalysis.Analyzer {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    @Volatile private var busy = false
    private var last = 0L

    override fun analyze(image: ImageProxy) {
        val mediaImage = image.image
        if (mediaImage == null || busy) { image.close(); return }
        val now = System.currentTimeMillis()
        if (now - last < 500) { image.close(); return }
        last = now
        busy = true
        val input = InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees)
        recognizer.process(input)
            .addOnSuccessListener { visionText ->
                // OCR is live and useful for the printed/handwritten letter areas.
                // The recognized text is surfaced for operator verification; it is not
                // silently converted into scored answers because OCR boxes must first
                // be registered to the exact answer-cell coordinates.
                val text = visionText.text
                callback(ScanState(
                    answers = emptyList(),
                    stable = true,
                    note = if (text.isBlank()) "Matn aniqlanmadi — varaqni tekisroq tuting" else "OCR ishlayapti. Natijani operator tasdiqlashi kerak.",
                    ocrText = text
                ))
            }
            .addOnFailureListener {
                callback(ScanState(note = "OCR tahlilida xatolik"))
            }
            .addOnCompleteListener {
                busy = false
                image.close()
            }
    }
}

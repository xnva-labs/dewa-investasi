package id.fajar.zahra.camera

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import java.io.File
import java.util.concurrent.Executors

@Composable
fun CameraProofScreen(missionId:Long=0L,proofType:String="PHOTO",expectedTarget:String="",onRecorded:(ProofResult)->Unit){
    val context=LocalContext.current;val lifecycleOwner=LocalLifecycleOwner.current
    var granted by remember{mutableStateOf(ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)}
    var error by remember{mutableStateOf<String?>(null)};var pending by remember{mutableStateOf<ProofResult?>(null)};var capture by remember{mutableStateOf<ImageCapture?>(null)}
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted=it}
    LaunchedEffect(Unit){if(!granted)launcher.launch(Manifest.permission.CAMERA)}
    val executor=remember{Executors.newSingleThreadExecutor()};DisposableEffect(Unit){onDispose{executor.shutdown()}}

    Box(Modifier.fillMaxSize()){
        if(granted&&pending==null){
            AndroidView(modifier=Modifier.fillMaxSize(),factory={ctx->
                val previewView=PreviewView(ctx);val future=ProcessCameraProvider.getInstance(ctx);future.addListener({runCatching{
                    val provider=future.get();val preview=Preview.Builder().build().also{it.surfaceProvider=previewView.surfaceProvider};val imageCapture=ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build();capture=imageCapture;provider.unbindAll();provider.bindToLifecycle(lifecycleOwner,CameraSelector.DEFAULT_BACK_CAMERA,preview,imageCapture)
                }.onFailure{error=it.message?:"Kamera gagal"}},ContextCompat.getMainExecutor(ctx));previewView})
            Button(modifier=Modifier.align(Alignment.BottomCenter).padding(24.dp),onClick={
                val imageCapture=capture?:return@Button;val file=File(context.cacheDir,"proof-${System.currentTimeMillis()}.jpg");val options=ImageCapture.OutputFileOptions.Builder(file).build();imageCapture.takePicture(options,executor,object:ImageCapture.OnImageSavedCallback{
                    override fun onImageSaved(outputFileResults:ImageCapture.OutputFileResults){try{
                        val image=InputImage.fromFilePath(context,Uri.fromFile(file));when(proofType){
                            "POSE"->PoseProofAnalyzer.analyze(image){e->file.delete();pending=ProofResult(missionId,"POSE","PENDING",e.message,e.confidence)}
                            "OBJECT"->ObjectProofAnalyzer.analyze(image,expectedTarget){e->file.delete();pending=ProofResult(missionId,"OBJECT","PENDING",e.message,e.confidence)}
                            else->{file.delete();pending=ProofResult(missionId,"PHOTO","PENDING","Foto diambil. Konfirmasi manual untuk menggunakan bukti.",1f)}
                        }
                    }catch(t:Throwable){file.delete();error=t.message?:"Analisis gagal"}}
                    override fun onError(exception:ImageCaptureException){error=exception.message?:"Gagal mengambil foto"}
                })
            }){Text("Ambil bukti")}
        }else if(!granted){Text("Kamera tidak diizinkan. Misi tetap dapat diselesaikan secara manual hanya jika misi tidak mewajibkan proof.",Modifier.align(Alignment.Center).padding(24.dp))}
        pending?.let{r->Card(Modifier.align(Alignment.Center).padding(24.dp)){Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Text("Tinjau proof",style=MaterialTheme.typography.titleLarge);Text(r.message);Text("Confidence: ${(r.confidence*100).toInt()}%",style=MaterialTheme.typography.labelSmall);Text("AI hanya memberi evidence; keputusan akhir tetap pada pengguna.",style=MaterialTheme.typography.bodySmall);Button({onRecorded(r.copy(status="CONFIRMED"));pending=null},Modifier.fillMaxWidth()){Text("Gunakan bukti")};OutlinedButton({onRecorded(r.copy(status="MANUAL_CONFIRMED",confidence=1f));pending=null},Modifier.fillMaxWidth()){Text("Konfirmasi manual")};TextButton({pending=null}){Text("Buang")}}}}
        error?.let{Text(it,Modifier.align(Alignment.TopCenter).padding(20.dp),color=MaterialTheme.colorScheme.error)}
    }
}

data class ProofResult(val missionId:Long,val proofType:String,val status:String,val message:String,val confidence:Float)

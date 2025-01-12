package com.example.reproductormusica

import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import android.Manifest
import android.net.Uri
import android.provider.Settings
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AlertDialog

class MainActivity : ComponentActivity() {

    private lateinit var search: SearchView
    private lateinit var play: ImageView
    private lateinit var pause: ImageView
    private lateinit var stop: ImageView
    private lateinit var add: ImageView

    private var mediaPlayer: MediaPlayer? = null

    // Crea un contrato de resultado para manejar el permiso
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // Permiso concedido
                Toast.makeText(this, "Permiso concedido para acceder al almacenamiento", Toast.LENGTH_SHORT).show()
                // Ahora abrir el selector de archivos
                openFileLauncher.launch("audio/*")
            } else {
                // Permiso denegado
                if (!shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                    // Si el usuario ya ha denegado permanentemente, mostramos un diálogo
                    showPermissionDeniedDialog()
                } else {
                    Toast.makeText(this, "Permiso denegado para acceder al almacenamiento", Toast.LENGTH_SHORT).show()
                }
            }
        }

    private val openFileLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                // Realizar alguna acción con el archivo seleccionado
                val filePath = it.path
                Toast.makeText(this, "Archivo seleccionado: $filePath", Toast.LENGTH_SHORT).show()
                playSelectedFile(it)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        // Inicializar las vistas
        search = findViewById(R.id.search)
        play = findViewById(R.id.play)
        pause = findViewById(R.id.pause)
        stop = findViewById(R.id.stop)
        add = findViewById(R.id.add)

        // Configurar el SearchView para mostrar búsquedas
        setupSearchView()

        add.setOnClickListener {
            // Verificar si el permiso de lectura del almacenamiento externo está concedido
            when {
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED -> {
                    // Si el permiso está concedido, abre el selector de archivos
                    openFileLauncher.launch("audio/*")
                }
                else -> {
                    // Si el permiso no está concedido, solicita el permiso
                    requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        // Liberar recursos del MediaPlayer
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun setupSearchView() {
        search.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                // Acción al buscar música
                Toast.makeText(this@MainActivity, "Buscando: $query", Toast.LENGTH_SHORT).show()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                // Acciones mientras se escribe en el campo de búsqueda
                return false
            }
        })
    }

    private fun playSelectedFile(uri: Uri) {
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, uri) // Establecer el URI del archivo
                prepare() // Prepara el MediaPlayer para la reproducción
                start() // Comienza la reproducción
            }
            Toast.makeText(this, "Reproduciendo archivo", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error al reproducir el archivo", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPermissionDeniedDialog() {
        // Mostrar un diálogo informativo para explicar cómo habilitar el permiso manualmente
        AlertDialog.Builder(this)
            .setTitle("Permiso requerido")
            .setMessage("Para seleccionar archivos de música, debe habilitar el permiso de almacenamiento en la configuración de la aplicación.")
            .setPositiveButton("Ir a Configuración") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
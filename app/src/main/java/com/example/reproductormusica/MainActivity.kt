package com.example.reproductormusica

import android.media.MediaPlayer
import android.os.Bundle
import android.net.Uri
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import java.io.IOException

class MainActivity : ComponentActivity() {

    private lateinit var list: ListView
    private lateinit var play: ImageView
    private lateinit var next: ImageView
    private lateinit var previous: ImageView
    private lateinit var add: ImageView

    private var mediaPlayer: MediaPlayer? = null

    // Lista para guardar los nombres de los archivos de música seleccionados
    private val songList = mutableListOf<String>()

    // Cancion seleccionada
    private var selectSong: Uri? = null

    // Directorio donde se guardarán las canciones
    private val musicDirectory: File by lazy {
        File(filesDir, "music")  // Usamos el directorio de archivos internos
    }

    // Declara un launcher para abrir el selector de archivos
    private val openFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {

            /// Guardamos el archivo de música en el directorio de la app
            val inputStream = contentResolver.openInputStream(it)
            val fileName = it.lastPathSegment ?: "Unknown Song"
            val destinationFile = File(musicDirectory, fileName)

            try {
                inputStream?.use { input ->
                    destinationFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: IOException) {
                Toast.makeText(this, "Error al guardar la canción", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        // Inicializar las vistas
        list = findViewById(R.id.searchSong)
        play = findViewById(R.id.play)
        next = findViewById(R.id.next)
        previous = findViewById(R.id.previous)
        add = findViewById(R.id.add)

        // Crea el directorio en caso de que no exista
        if (!musicDirectory.exists()) {
            musicDirectory.mkdirs()
        }

        updateList()
    }

    override fun onStart() {
        super.onStart()

        add.setOnClickListener {
            openFileLauncher.launch("audio/*")
        }

        // Configurar clics en la lista
        list.setOnItemClickListener { _, _, position, _ ->
            val selectedSong = songList[position]
            val songUri = Uri.fromFile(File(musicDirectory, selectedSong))
            selectSong = songUri
            playSelectedFile(songUri)
        }

        play.setOnClickListener {
            selectSong?.let { uri ->
                playSelectedFile(uri)
            } ?: Toast.makeText(this, "Selecciona una canción primero", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPause() {
        super.onPause()
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        // Liberar recursos del MediaPlayer
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun playSelectedFile(uri: Uri) {
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, uri) // Establecer el URI del archivo
                prepare() // Prepara el MediaPlayer para la reproducción
                start() // Comienza la reproducción
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error al reproducir el archivo", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateList(query: String? = null) {
        // Limpiar la lista de canciones antes de agregar nuevas
        songList.clear()

        // Obtener todos los archivos de música del directorio
        val musicFiles = musicDirectory.listFiles()?.filter { file ->
            // Mostrar solo los archivos de sonido
            file.isFile && (file.name.endsWith(".mp3") || file.name.endsWith(".wav"))
        }

        // Si hay archivos de música, añadirlos a la lista
        musicFiles?.forEach { file ->
            songList.add(file.name)
        }

        // Si hay un filtro de búsqueda (query), aplicar el filtro
        val filteredList = if (!query.isNullOrEmpty()) {
            songList.filter { it.contains(query, ignoreCase = true) }
        } else {
            songList
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, filteredList )
        list.adapter = adapter
    }
}
package com.example.reproductormusica

import android.media.MediaPlayer
import android.os.Bundle
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.SeekBar
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

    private lateinit var seekBar: SeekBar
    private val handler = Handler(Looper.getMainLooper())

    private var mediaPlayer: MediaPlayer? = null

    // Lista para guardar los nombres de los archivos de música seleccionados
    private val songList = mutableListOf<String>()

    // Cancion seleccionada
    private var selectSong: Uri? = null
    private var currentSongIndex = -1

    // Variables para gestionar el SeekBar
    private var isSeekBarTouching = false

    // Directorio donde se guardarán las canciones
    private val musicDirectory: File by lazy {
        File(filesDir, "music")  // Usamos el directorio de archivos internos
    }

    // Declara un launcher para abrir el selector de archivos
    private val openFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            /// Guardamos el archivo de música en el directorio de la app
            val inputStream = contentResolver.openInputStream(it)

            // Obtener el nombre del archivo real desde el ContentResolver
            val cursor = contentResolver.query(it, null, null, null, null)
            val fileName = cursor?.use { c ->
                val nameIndex = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                c.moveToFirst()
                c.getString(nameIndex) ?: "Unknown Song"
            } ?: "Unknown Song"
            val destinationFile = File(musicDirectory, fileName)

            if (destinationFile.exists()) {
                Toast.makeText(this, "El archivo ya existe", Toast.LENGTH_SHORT).show()
                return@let
            }

            try {
                inputStream?.use { input ->
                    destinationFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                // Actualizamos la lista después de guardar la canción
                updateList()
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
        seekBar = findViewById(R.id.progressBar)
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

        next.setOnClickListener {
            playNext()
        }

        previous.setOnClickListener {
            playPrevious()
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mediaPlayer?.seekTo(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isSeekBarTouching = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isSeekBarTouching = false
            }
        })
    }

    override fun onPause() {
        super.onPause()
        if (mediaPlayer?.isPlaying == true) {
            handler.removeCallbacksAndMessages(null) // Detener los callbacks del Handler
            mediaPlayer?.pause()
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        // Liberar recursos del MediaPlayer
        mediaPlayer?.release()
        mediaPlayer = null
        handler.removeCallbacksAndMessages(null) // Detener los callbacks del Handler
    }

    private fun playSelectedFile(uri: Uri) {
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, uri) // Establecer el URI del archivo
                prepare() // Prepara el MediaPlayer para la reproducción
                start() // Comienza la reproducción

                seekBar.max = duration // Duración total de la canción
                updateSeekBar()
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
            // Usa un cursor para extraer la información del archivo
            val uri = Uri.fromFile(file)
            val cursor = contentResolver.query(uri, null, null, null, null)
            val fileName = cursor?.use { c ->
                val nameIndex = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (c.moveToFirst() && nameIndex != -1) {
                    c.getString(nameIndex)
                } else {
                    file.name // Usa el nombre del archivo como respaldo
                }
            } ?: file.name // Si el cursor no funciona, usa el nombre del archivo directamente

            songList.add(fileName)
        }

        // Si hay un filtro de búsqueda (query), aplicar el filtro
        val filteredList = if (!query.isNullOrEmpty()) {
            songList.filter { it.contains(query, ignoreCase = true) }
        } else {
            songList
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, filteredList )
        list.adapter = adapter

        // Notificar al adaptador para que actualice la vista
        adapter.notifyDataSetChanged()
    }

    private fun playNext() {
        if (songList.isNotEmpty()) {
            currentSongIndex = (currentSongIndex + 1) % songList.size
            val nextSong = songList[currentSongIndex]
            playSelectedFile(Uri.fromFile(File(musicDirectory, nextSong)))
        }
    }

    private fun playPrevious() {
        if (songList.isNotEmpty()) {
            currentSongIndex = if (currentSongIndex - 1 < 0) songList.size - 1 else currentSongIndex - 1
            val previousSong = songList[currentSongIndex]
            playSelectedFile(Uri.fromFile(File(musicDirectory, previousSong)))
        }
    }

    private fun updateSeekBar() {
        if (mediaPlayer != null && !isSeekBarTouching) {
            seekBar.progress = mediaPlayer!!.currentPosition
        }

        // Vuelve a ejecutar este método después de 1 segundo
        handler.postDelayed({ updateSeekBar() }, 1000)
    }

}
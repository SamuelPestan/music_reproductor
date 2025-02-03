package com.example.reproductormusica

import android.annotation.SuppressLint
import android.media.MediaPlayer
import android.os.Bundle
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.io.File
import java.io.IOException

class MainActivity : ComponentActivity() {

    private lateinit var play: ImageView
    private lateinit var next: ImageView
    private lateinit var previous: ImageView
    private lateinit var add: ImageView
    private lateinit var title: TextView
    private lateinit var searchSong: ImageView

    private lateinit var seekBar: SeekBar
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var startTime: TextView
    private lateinit var endTime: TextView

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
        searchSong = findViewById(R.id.searchBtn)
        play = findViewById(R.id.play)
        next = findViewById(R.id.next)
        previous = findViewById(R.id.previous)
        add = findViewById(R.id.add)
        seekBar = findViewById(R.id.progressBar)
        startTime = findViewById(R.id.startTime)
        endTime = findViewById(R.id.endTime)
        title = findViewById(R.id.title)

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

        searchSong.setOnClickListener {
            if (songList.isEmpty()) {
                Toast.makeText(this, "No hay canciones disponibles", Toast.LENGTH_SHORT).show()
            } else {
                showBottomSheetDialog() // Mostrar la lista de canciones con el diálogo
            }
        }

        play.setOnClickListener {
            if (mediaPlayer != null && mediaPlayer?.isPlaying == true) {
                // Si el MediaPlayer está reproduciendo, pausamos
                mediaPlayer?.pause()
                play.setImageResource(R.drawable.ic_play) // Cambia a ícono de "play"
            } else {
                selectSong?.let { uri ->
                    if (mediaPlayer == null) {
                        // Si no hay un MediaPlayer activo, creamos uno nuevo
                        playSelectedFile(uri)
                    } else {
                        // Si el MediaPlayer existe pero está pausado, continuamos
                        mediaPlayer?.start()
                        play.setImageResource(R.drawable.ic_pause) // Cambia a ícono de "pausa"
                    }
                } ?: Toast.makeText(this, "Selecciona una canción primero", Toast.LENGTH_SHORT).show()
            }
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
                    // Actualiza el tiempo de inicio en tiempo real mientras el usuario mueve el SeekBar
                    startTime.text = formatTime(progress)
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
            // Detener reproducción actual si existe
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null

            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, uri) // Establecer el URI del archivo
                prepare() // Prepara el MediaPlayer para la reproducción
                start() // Comienza la reproducción

                seekBar.max = duration // Duración total de la canción
                updateSeekBar()
            }

            // Actualiza el título de la aplicación con el nombre de la canción (sin extensión)
            val fileName = File(uri.path ?: "").nameWithoutExtension
            title.text = fileName

            // Asegura que el botón de reproducción tenga el ícono de pausa
            play.setImageResource(R.drawable.ic_pause)
        } catch (e: Exception) {
            Toast.makeText(this, "Error al reproducir el archivo", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateList() {
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
        mediaPlayer?.let { mp ->
            if (!isSeekBarTouching) {
                // Actualiza el progreso del SeekBar si no está siendo tocado
                seekBar.progress = mp.currentPosition
                // Actualiza los textos de inicio y fin
                startTime.text = formatTime(mp.currentPosition)
                endTime.text = formatTime(mp.duration)
            }
        }

        // Vuelve a ejecutar este metodo después de 1 segundo
        handler.postDelayed({ updateSeekBar() }, 1000)
    }

    @SuppressLint("DefaultLocale")
    private fun formatTime(millis: Int): String {
        val minutes = millis / 1000 / 60
        val seconds = millis / 1000 % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    @SuppressLint("InflateParams")
    private fun showBottomSheetDialog() {
        // Crear un BottomSheetDialog
        val bottomSheetDialog = BottomSheetDialog(this)

        // Inflar la vista personalizada para el diálogo
        val view = layoutInflater.inflate(R.layout.bottom_sheet_song_list, null)
        bottomSheetDialog.setContentView(view)

        // Obtener referencia al ListView dentro del BottomSheet
        val songListView: ListView = view.findViewById(R.id.songListView)
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, songList)
        songListView.adapter = adapter

        // Configurar clics en los elementos de la lista
        songListView.setOnItemClickListener { _, _, position, _ ->
            val selectedSong = songList[position]
            val songUri = Uri.fromFile(File(musicDirectory, selectedSong))
            selectSong = songUri
            playSelectedFile(songUri)
            bottomSheetDialog.dismiss() // Cerrar el diálogo después de seleccionar una canción
        }

        bottomSheetDialog.show()
    }

}
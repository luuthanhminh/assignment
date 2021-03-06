package com.zilla.android.ui.player

import android.media.MediaPlayer
import android.util.Log
import com.zilla.android.models.PlayList
import com.zilla.android.models.Song
import java.io.IOException
import java.util.ArrayList

class Player private constructor() : IPlayback, MediaPlayer.OnCompletionListener {
    override val duration: Int
        get() = mPlayer!!.duration

    private var mPlayer: MediaPlayer? = null

    private var mPlayList: PlayList? = null
    // Default size 2: for service and UI
    private val mCallbacks = ArrayList<IPlayback.Callback>(2)

    // Player status
    private var isPaused: Boolean = false

    override val isPlaying: Boolean
        get() = mPlayer!!.isPlaying

    override val progress: Int
        get() = mPlayer!!.currentPosition

    override val playingSong: Song
        get() = mPlayList!!.getCurrentSong()!!

    init {
        mPlayer = MediaPlayer()
        mPlayList = PlayList()
        mPlayer!!.setOnCompletionListener(this)
    }

    override fun setPlayList(list: PlayList) {
        var list = list
        if (list == null) {
            list = PlayList()
        }
        mPlayList = list
    }

    override fun play(list: PlayList): Boolean {
        if (list == null) return false

        isPaused = false
        setPlayList(list)
        return play()
    }

    override fun play(list: PlayList, startIndex: Int): Boolean {
        if (list == null || startIndex < 0 || startIndex >= list.numOfSongs) return false

        isPaused = false
        list!!.setPlayingIndex(startIndex)
        setPlayList(list)
        return play()
    }

    override fun play(song: Song): Boolean {
        if (song == null) return false

        isPaused = false
        mPlayList!!.getSongs().clear()
        mPlayList!!.getSongs().add(song)
        return play()
    }


    override fun play(): Boolean {
        if (isPaused) {
            mPlayer!!.start()
            notifyPlayStatusChanged(true)
            return true
        }
        if (mPlayList!!.prepare()) {
            val song = mPlayList!!.getCurrentSong()
            try {
                mPlayer!!.reset()
                mPlayer!!.setDataSource(song!!.path)
                mPlayer!!.prepare()
                mPlayer!!.start()
                notifyPlayStatusChanged(true)
            } catch (e: IOException) {
                Log.e(TAG, "play: ", e)
                notifyPlayStatusChanged(false)
                return false
            }

            return true
        }
        return false
    }


    override fun playLast(): Boolean {
        isPaused = false
        val hasLast = mPlayList!!.hasLast()
        if (hasLast) {
            val last = mPlayList!!.last()
            play()
            notifyPlayLast(last)
            return true
        }
        return false
    }

    override fun playNext(): Boolean {
        isPaused = false
        val hasNext = mPlayList!!.hasNext(false)
        if (hasNext) {
            val next = mPlayList!!.next()
            play()
            notifyPlayNext(next)
            return true
        }
        return false
    }

    override fun pause(): Boolean {
        if (mPlayer!!.isPlaying) {
            mPlayer!!.pause()
            isPaused = true
            notifyPlayStatusChanged(false)
            return true
        }
        return false
    }

    override fun seekTo(progress: Int): Boolean {
        if (mPlayList!!.getSongs().isEmpty()) return false

        val currentSong = mPlayList!!.getCurrentSong()
        if (currentSong != null) {
            if (currentSong!!.duration <= progress) {
                onCompletion(mPlayer)
            } else {
                mPlayer!!.seekTo(progress)
            }
            return true
        }
        return false
    }

    override fun setPlayMode(playMode: PlayMode) {
        mPlayList!!.setPlayMode(playMode)
    }

    // Listeners

    override fun onCompletion(mp: MediaPlayer?) {
        var next: Song? = null
        // There is only one limited play mode which is list, player should be stopped when hitting the list end
        if (mPlayList!!.getPlayMode() === PlayMode.LIST && mPlayList!!.getPlayingIndex() === mPlayList!!.numOfSongs - 1) {
            // In the end of the list
            // Do nothing, just deliver the callback
        } else if (mPlayList!!.getPlayMode() === PlayMode.SINGLE) {
            next = mPlayList!!.getCurrentSong()
            play()
        } else {
            val hasNext = mPlayList!!.hasNext(true)
            if (hasNext) {
                next = mPlayList!!.next()
                play()
            }
        }
        notifyComplete(next)
    }

    override fun releasePlayer() {
        mPlayList = null
        mPlayer!!.reset()
        mPlayer!!.release()
        mPlayer = null
        sInstance = null
    }

    // Callbacks

    override fun registerCallback(callback: IPlayback.Callback) {
        mCallbacks.add(callback)
    }

    override fun unregisterCallback(callback: IPlayback.Callback) {
        mCallbacks.remove(callback)
    }

    override fun removeCallbacks() {
        mCallbacks.clear()
    }

    private fun notifyPlayStatusChanged(isPlaying: Boolean) {
        for (callback in mCallbacks) {
            callback.onPlayStatusChanged(isPlaying)
        }
    }

    private fun notifyPlayLast(song: Song) {
        for (callback in mCallbacks) {
            callback.onSwitchLast(song)
        }
    }

    private fun notifyPlayNext(song: Song) {
        for (callback in mCallbacks) {
            callback.onSwitchNext(song)
        }
    }

    private fun notifyComplete(song: Song?) {
        for (callback in mCallbacks) {
            callback.onComplete(song)
        }
    }

    companion object {

        private val TAG = "Player"

        @Volatile
        private var sInstance: Player? = null

        val instance: Player?
            get() {
                if (sInstance == null) {
                    synchronized(Player::class.java) {
                        if (sInstance == null) {
                            sInstance = Player()
                        }
                    }
                }
                return sInstance
            }
    }
}
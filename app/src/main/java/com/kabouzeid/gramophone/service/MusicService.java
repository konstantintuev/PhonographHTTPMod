package com.kabouzeid.http.service;

import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.MediaScannerConnection;
import android.media.audiofx.AudioEffect;
import android.media.session.MediaSession;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.Process;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.util.SparseArray;
import android.widget.Toast;

import com.bumptech.glide.BitmapRequestBuilder;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.SimpleTarget;
import com.kabouzeid.http.R;
import com.kabouzeid.http.appwidgets.AppWidgetBig;
import com.kabouzeid.http.appwidgets.AppWidgetCard;
import com.kabouzeid.http.appwidgets.AppWidgetClassic;
import com.kabouzeid.http.appwidgets.AppWidgetSmall;
import com.kabouzeid.http.glide.BlurTransformation;
import com.kabouzeid.http.glide.SongGlideRequest;
import com.kabouzeid.http.helper.ShuffleHelper;
import com.kabouzeid.http.helper.StopWatch;
import com.kabouzeid.http.loader.PlaylistSongLoader;
import com.kabouzeid.http.model.AbsCustomPlaylist;
import com.kabouzeid.http.model.Playlist;
import com.kabouzeid.http.model.Song;
import com.kabouzeid.http.provider.HistoryStore;
import com.kabouzeid.http.provider.MusicPlaybackQueueStore;
import com.kabouzeid.http.provider.SongPlayCountStore;
import com.kabouzeid.http.service.notification.PlayingNotification;
import com.kabouzeid.http.service.notification.PlayingNotificationImpl;
import com.kabouzeid.http.service.notification.PlayingNotificationImpl24;
import com.kabouzeid.http.service.playback.Playback;
import com.kabouzeid.http.util.MusicUtil;
import com.kabouzeid.http.util.PreferenceUtil;
import com.kabouzeid.http.util.Util;
import com.kabouzeid.http.wifihotspotutils.WifiApManager;

import org.java_websocket.WebSocket;
import org.java_websocket.WebSocketImpl;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import at.huber.youtubeExtractor.VideoMeta;
import at.huber.youtubeExtractor.YouTubeExtractor;
import at.huber.youtubeExtractor.YtFile;
import fi.iki.elonen.NanoHTTPD;

/**
 * @author Karim Abou Zeid (kabouzeid), Andrew Neal
 */
public class MusicService extends Service implements SharedPreferences.OnSharedPreferenceChangeListener, Playback.PlaybackCallbacks {
    public static final String TAG = MusicService.class.getSimpleName();

    public static final String PHONOGRAPH_PACKAGE_NAME = "com.kabouzeid.gramophone" + ".temp_sticky_intent_fix"; // TODO remove ".temp_sticky_intent_fix" in a future update.
    public static final String MUSIC_PACKAGE_NAME = "com.android.music";

    public static final String ACTION_TOGGLE_PAUSE = PHONOGRAPH_PACKAGE_NAME + ".togglepause";
    public static final String ACTION_PLAY = PHONOGRAPH_PACKAGE_NAME + ".play";
    public static final String ACTION_PLAY_PLAYLIST = PHONOGRAPH_PACKAGE_NAME + ".play.playlist";
    public static final String ACTION_PAUSE = PHONOGRAPH_PACKAGE_NAME + ".pause";
    public static final String ACTION_STOP = PHONOGRAPH_PACKAGE_NAME + ".stop";
    public static final String ACTION_SKIP = PHONOGRAPH_PACKAGE_NAME + ".skip";
    public static final String ACTION_REWIND = PHONOGRAPH_PACKAGE_NAME + ".rewind";
    public static final String ACTION_QUIT = PHONOGRAPH_PACKAGE_NAME + ".quitservice";
    public static final String INTENT_EXTRA_PLAYLIST = PHONOGRAPH_PACKAGE_NAME + "intentextra.playlist";
    public static final String INTENT_EXTRA_SHUFFLE_MODE = PHONOGRAPH_PACKAGE_NAME + ".intentextra.shufflemode";

    public static final String APP_WIDGET_UPDATE = PHONOGRAPH_PACKAGE_NAME + ".appwidgetupdate";
    public static final String EXTRA_APP_WIDGET_NAME = PHONOGRAPH_PACKAGE_NAME + "app_widget_name";

    // do not change these three strings as it will break support with other apps (e.g. last.fm scrobbling)
    public static final String META_CHANGED = PHONOGRAPH_PACKAGE_NAME + ".metachanged";
    public static final String QUEUE_CHANGED = PHONOGRAPH_PACKAGE_NAME + ".queuechanged";
    public static final String PLAY_STATE_CHANGED = PHONOGRAPH_PACKAGE_NAME + ".playstatechanged";

    public static final String REPEAT_MODE_CHANGED = PHONOGRAPH_PACKAGE_NAME + ".repeatmodechanged";
    public static final String SHUFFLE_MODE_CHANGED = PHONOGRAPH_PACKAGE_NAME + ".shufflemodechanged";
    public static final String MEDIA_STORE_CHANGED = PHONOGRAPH_PACKAGE_NAME + ".mediastorechanged";

    public static final String SAVED_POSITION = "POSITION";
    public static final String SAVED_POSITION_IN_TRACK = "POSITION_IN_TRACK";
    public static final String SAVED_SHUFFLE_MODE = "SHUFFLE_MODE";
    public static final String SAVED_REPEAT_MODE = "REPEAT_MODE";

    public static final int RELEASE_WAKELOCK = 0;
    public static final int TRACK_ENDED = 1;
    public static final int TRACK_WENT_TO_NEXT = 2;
    public static final int PLAY_SONG = 3;
    public static final int PREPARE_NEXT = 4;
    public static final int SET_POSITION = 5;
    private static final int FOCUS_CHANGE = 6;
    private static final int DUCK = 7;
    private static final int UNDUCK = 8;
    public static final int RESTORE_QUEUES = 9;

    public static final int SHUFFLE_MODE_NONE = 0;
    public static final int SHUFFLE_MODE_SHUFFLE = 1;

    public static final int REPEAT_MODE_NONE = 0;
    public static final int REPEAT_MODE_ALL = 1;
    public static final int REPEAT_MODE_THIS = 2;

    public static final int SAVE_QUEUES = 0;

    private final IBinder musicBind = new MusicBinder();

    private AppWidgetBig appWidgetBig = AppWidgetBig.getInstance();
    private AppWidgetClassic appWidgetClassic = AppWidgetClassic.getInstance();
    private AppWidgetSmall appWidgetSmall = AppWidgetSmall.getInstance();
    private AppWidgetCard appWidgetCard = AppWidgetCard.getInstance();

    private Playback playback;
    private ArrayList<Song> playingQueue = new ArrayList<>();
    private ArrayList<Song> originalPlayingQueue = new ArrayList<>();
    private int position = -1;
    private int nextPosition = -1;
    private int shuffleMode;
    private int repeatMode;
    private boolean queuesRestored;
    private boolean pausedByTransientLossOfFocus;
    private PlayingNotification playingNotification;
    private AudioManager audioManager;
    @SuppressWarnings("deprecation")
    private MediaSessionCompat mediaSession;
    private PowerManager.WakeLock wakeLock;
    private PlaybackHandler playerHandler;
    private final AudioManager.OnAudioFocusChangeListener audioFocusListener = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(final int focusChange) {
            playerHandler.obtainMessage(FOCUS_CHANGE, focusChange, 0).sendToTarget();
        }
    };
    private QueueSaveHandler queueSaveHandler;
    private HandlerThread musicPlayerHandlerThread;
    private HandlerThread queueSaveHandlerThread;
    private SongPlayCountHelper songPlayCountHelper = new SongPlayCountHelper();
    private ThrottledSeekHandler throttledSeekHandler;
    private boolean becomingNoisyReceiverRegistered;
    private IntentFilter becomingNoisyReceiverIntentFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
    private final BroadcastReceiver becomingNoisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, @NonNull Intent intent) {
            if (intent.getAction().equals(AudioManager.ACTION_AUDIO_BECOMING_NOISY)) {
                pause();
            }
        }
    };
    private ContentObserver mediaStoreObserver;
    private boolean notHandledMetaChangedForCurrentTrack;
    private boolean isServiceBound;

    private Handler uiThreadHandler;
    private AndroidWebServer androidWebServer;
    private float max;
    WifiManager mWifiManager;
    private boolean canDo = true;
    private String ip;

    private static String getTrackUri(@NonNull Song song) {
        return MusicUtil.getSongFileUri(song.id).toString();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        final PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getClass().getName());
        wakeLock.setReferenceCounted(false);

        musicPlayerHandlerThread = new HandlerThread("PlaybackHandler");
        musicPlayerHandlerThread.start();
        playerHandler = new PlaybackHandler(this, musicPlayerHandlerThread.getLooper());

        playback = new MultiPlayer(this);
        playback.setCallbacks(this);

        setupMediaSession();

        // queue saving needs to run on a separate thread so that it doesn't block the playback handler events
        queueSaveHandlerThread = new HandlerThread("QueueSaveHandler", Process.THREAD_PRIORITY_BACKGROUND);
        queueSaveHandlerThread.start();
        queueSaveHandler = new QueueSaveHandler(this, queueSaveHandlerThread.getLooper());

        uiThreadHandler = new Handler();

        registerReceiver(widgetIntentReceiver, new IntentFilter(APP_WIDGET_UPDATE));

        initNotification();

        mediaStoreObserver = new MediaStoreObserver(playerHandler);
        throttledSeekHandler = new ThrottledSeekHandler(playerHandler);
        getContentResolver().registerContentObserver(
                MediaStore.Audio.Media.INTERNAL_CONTENT_URI, true, mediaStoreObserver);
        getContentResolver().registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, mediaStoreObserver);

        PreferenceUtil.getInstance(this).registerOnSharedPreferenceChangedListener(this);

        restoreState();

        mediaSession.setActive(true);

        sendBroadcast(new Intent("com.kabouzeid.gramophone.PHONOGRAPH_MUSIC_SERVICE_CREATED"));

        //TODO: my mods

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            canDo = Settings.System.canWrite(this);
            if (!canDo)
            {
                Intent grantIntent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                grantIntent.setData(Uri.parse("package:"+this.getPackageName()));
                startActivity(grantIntent);
            }
        }

        getAudioManager();
        mWifiManager=(WifiManager)  getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        if (audioManager != null) {
            max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        }

        registerReceiver(downloadReceiver, new IntentFilter(getPackageName()+".controlService"));

        androidWebServer = new AndroidWebServer(8080);

        new getIndex().execute(MusicService.this);
    }

    BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @SuppressWarnings("ConstantConditions")
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle extras = intent.getExtras();
            if (extras != null && extras.getString("action") != null) {
                switch (extras.getString("action")) {
                    case "download":
                        if(extras.getString("video") != null) {
                            String pattern = "(?<=watch\\?v=|/videos/|embed\\/|youtu.be\\/|\\/v\\/|\\/e\\/|watch\\?v%3D|watch\\?feature=player_embedded&v=|%2Fvideos%2F|embed%\u200C\u200B2F|youtu.be%2F|%2Fv%2F)[^#\\&\\?\\n]*";

                            Pattern compiledPattern = Pattern.compile(pattern);
                            Matcher matcher = compiledPattern.matcher(extras.getString("video")); //url is youtube url for which you want to extract the id.
                            if (matcher.find()) {
                                downloadVideoYT("https://www.youtube.com/watch?v=" + matcher.group());
                            }
                        }
                        break;
                    case "online":
                        boolean value = extras.getBoolean("value");
                        if (value != PreferenceUtil.getInstance(MusicService.this).getOnline()) {
                            if (value) {
                                WifiApManager wifiApManager = new WifiApManager(MusicService.this);
                                if (canDo) {
                                    wifiApManager.setWifiApEnabled(null, false);
                                } else {
                                    wifiApManager.justWifi(true);
                                }
                            } else {
                                if (canDo) {
                                    WifiApManager wifiApManager = new WifiApManager(MusicService.this);
                                    WifiConfiguration wifiCon = new WifiConfiguration();
                                    wifiCon.SSID = "Sony Music";
                                    wifiCon.preSharedKey = "87654321";
                                    wifiCon.allowedKeyManagement.set(4);
                                    wifiCon.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
                                    wifiApManager.setWifiApConfiguration(wifiCon);
                                    wifiApManager.setWifiApEnabled(wifiCon, true);
                                }
                                final Intent broadcastReceiver = new Intent(Intent.ACTION_MAIN, null);
                                broadcastReceiver.addCategory(Intent.CATEGORY_LAUNCHER);
                                final ComponentName cn = new ComponentName("com.android.settings", "com.android.settings.TetherSettings");
                                broadcastReceiver.setComponent(cn);
                                broadcastReceiver.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(broadcastReceiver);
                                Toast.makeText(MusicService.this, "The URL is 192.168.43.1:8080!\nEnable Wifi Hotspot!", Toast.LENGTH_LONG).show();
                            }
                            (new Handler()).postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    createWebSocketClient();
                                }
                            }, 20000);
                            PreferenceUtil.getInstance(MusicService.this).setOnline(value);
                        }
                        break;
                }

            }
        }
    };

    private AudioManager getAudioManager() {
        if (audioManager == null) {
            audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        }
        return audioManager;
    }

    private void setupMediaSession() {
        ComponentName mediaButtonReceiverComponentName = new ComponentName(getApplicationContext(), MediaButtonIntentReceiver.class);

        Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        mediaButtonIntent.setComponent(mediaButtonReceiverComponentName);

        PendingIntent mediaButtonReceiverPendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, mediaButtonIntent, 0);

        mediaSession = new MediaSessionCompat(this, "Phonograph", mediaButtonReceiverComponentName, mediaButtonReceiverPendingIntent);
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                play();
            }

            @Override
            public void onPause() {
                pause();
            }

            @Override
            public void onSkipToNext() {
                playNextSong(true);
            }

            @Override
            public void onSkipToPrevious() {
                back(true);
            }

            @Override
            public void onStop() {
                quit();
            }

            @Override
            public void onSeekTo(long pos) {
                seek((int) pos);
            }

            @Override
            public boolean onMediaButtonEvent(Intent mediaButtonEvent) {
                return MediaButtonIntentReceiver.handleIntent(MusicService.this, mediaButtonEvent);
            }
        });

        mediaSession.setFlags(MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
                | MediaSession.FLAG_HANDLES_MEDIA_BUTTONS);

        mediaSession.setMediaButtonReceiver(mediaButtonReceiverPendingIntent);
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        if (intent != null) {
            if (intent.getAction() != null) {
                restoreQueuesAndPositionIfNecessary();
                String action = intent.getAction();
                switch (action) {
                    case ACTION_TOGGLE_PAUSE:
                        if (isPlaying()) {
                            pause();
                        } else {
                            play();
                        }
                        break;
                    case ACTION_PAUSE:
                        pause();
                        break;
                    case ACTION_PLAY:
                        play();
                        break;
                    case ACTION_PLAY_PLAYLIST:
                        Playlist playlist = intent.getParcelableExtra(INTENT_EXTRA_PLAYLIST);
                        int shuffleMode = intent.getIntExtra(INTENT_EXTRA_SHUFFLE_MODE, getShuffleMode());
                        if (playlist != null) {
                            ArrayList<Song> playlistSongs;
                            if (playlist instanceof AbsCustomPlaylist) {
                                playlistSongs = ((AbsCustomPlaylist) playlist).getSongs(getApplicationContext());
                            } else {
                                //noinspection unchecked
                                playlistSongs = (ArrayList<Song>) (List) PlaylistSongLoader.getPlaylistSongList(getApplicationContext(), playlist.id);
                            }
                            if (!playlistSongs.isEmpty()) {
                                if (shuffleMode == SHUFFLE_MODE_SHUFFLE) {
                                    int startPosition = 0;
                                    if (!playlistSongs.isEmpty()) {
                                        startPosition = new Random().nextInt(playlistSongs.size());
                                    }
                                    openQueue(playlistSongs, startPosition, true);
                                    setShuffleMode(shuffleMode);
                                } else {
                                    openQueue(playlistSongs, 0, true);
                                }
                            } else {
                                Toast.makeText(getApplicationContext(), R.string.playlist_is_empty, Toast.LENGTH_LONG).show();
                            }
                        } else {
                            Toast.makeText(getApplicationContext(), R.string.playlist_is_empty, Toast.LENGTH_LONG).show();
                        }
                        break;
                    case ACTION_REWIND:
                        back(true);
                        break;
                    case ACTION_SKIP:
                        playNextSong(true);
                        break;
                    case ACTION_STOP:
                    case ACTION_QUIT:
                        return quit();
                }
            }
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(widgetIntentReceiver);
        if (becomingNoisyReceiverRegistered) {
            unregisterReceiver(becomingNoisyReceiver);
            becomingNoisyReceiverRegistered = false;
        }
        unregisterReceiver(downloadReceiver);
        mediaSession.setActive(false);
        quit();
        releaseResources();
        getContentResolver().unregisterContentObserver(mediaStoreObserver);
        PreferenceUtil.getInstance(this).unregisterOnSharedPreferenceChangedListener(this);
        wakeLock.release();

        sendBroadcast(new Intent("com.kabouzeid.gramophone.PHONOGRAPH_MUSIC_SERVICE_DESTROYED"));
        //TODO: my mods
        androidWebServer.stop();
        try {
            webSocketServer.stop();
        } catch (Exception e) {
            e.printStackTrace();
        }
        Log.d(TAG, "onDestroy: music");
        stopSelf();
    }

    @Override
    public IBinder onBind(Intent intent) {
        isServiceBound = true;
        return musicBind;
    }

    @Override
    public void onRebind(Intent intent) {
        isServiceBound = true;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        isServiceBound = false;
        if (!isPlaying()) {
            stopSelf();
        }
        return true;
    }

    private static final class QueueSaveHandler extends Handler {
        @NonNull
        private final WeakReference<MusicService> mService;

        public QueueSaveHandler(final MusicService service, @NonNull final Looper looper) {
            super(looper);
            mService = new WeakReference<>(service);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            final MusicService service = mService.get();
            switch (msg.what) {
                case SAVE_QUEUES:
                    service.saveQueuesImpl();
                    break;
            }
        }
    }

    private void saveQueuesImpl() {
        MusicPlaybackQueueStore.getInstance(this).saveQueues(playingQueue, originalPlayingQueue);
    }

    private void savePosition() {
        PreferenceManager.getDefaultSharedPreferences(this).edit().putInt(SAVED_POSITION, getPosition()).apply();
    }

    private void savePositionInTrack() {
        PreferenceManager.getDefaultSharedPreferences(this).edit().putInt(SAVED_POSITION_IN_TRACK, getSongProgressMillis()).apply();
    }

    public void saveState() {
        saveQueues();
        savePosition();
        savePositionInTrack();
    }

    private void saveQueues() {
        queueSaveHandler.removeMessages(SAVE_QUEUES);
        queueSaveHandler.sendEmptyMessage(SAVE_QUEUES);
    }

    private void restoreState() {
        shuffleMode = PreferenceManager.getDefaultSharedPreferences(this).getInt(SAVED_SHUFFLE_MODE, 0);
        repeatMode = PreferenceManager.getDefaultSharedPreferences(this).getInt(SAVED_REPEAT_MODE, 0);
        handleAndSendChangeInternal(SHUFFLE_MODE_CHANGED);
        handleAndSendChangeInternal(REPEAT_MODE_CHANGED);

        playerHandler.removeMessages(RESTORE_QUEUES);
        playerHandler.sendEmptyMessage(RESTORE_QUEUES);
    }

    private synchronized void restoreQueuesAndPositionIfNecessary() {
        if (!queuesRestored && playingQueue.isEmpty()) {
            ArrayList<Song> restoredQueue = MusicPlaybackQueueStore.getInstance(this).getSavedPlayingQueue();
            ArrayList<Song> restoredOriginalQueue = MusicPlaybackQueueStore.getInstance(this).getSavedOriginalPlayingQueue();
            int restoredPosition = PreferenceManager.getDefaultSharedPreferences(this).getInt(SAVED_POSITION, -1);
            int restoredPositionInTrack = PreferenceManager.getDefaultSharedPreferences(this).getInt(SAVED_POSITION_IN_TRACK, -1);

            if (restoredQueue.size() > 0 && restoredQueue.size() == restoredOriginalQueue.size() && restoredPosition != -1) {
                this.originalPlayingQueue = restoredOriginalQueue;
                this.playingQueue = restoredQueue;

                position = restoredPosition;
                openCurrent();
                prepareNext();

                if (restoredPositionInTrack > 0) seek(restoredPositionInTrack);

                notHandledMetaChangedForCurrentTrack = true;
                sendChangeInternal(META_CHANGED);
                sendChangeInternal(QUEUE_CHANGED);
            }
        }
        queuesRestored = true;
    }

    private int quit() {
        pause();
        playingNotification.stop();

        if (isServiceBound) {
            return START_STICKY;
        } else {
            closeAudioEffectSession();
            getAudioManager().abandonAudioFocus(audioFocusListener);
            stopSelf();
            return START_NOT_STICKY;
        }
    }

    private void releaseResources() {
        playerHandler.removeCallbacksAndMessages(null);
        if (Build.VERSION.SDK_INT >= 18) {
            musicPlayerHandlerThread.quitSafely();
        } else {
            musicPlayerHandlerThread.quit();
        }
        queueSaveHandler.removeCallbacksAndMessages(null);
        if (Build.VERSION.SDK_INT >= 18) {
            queueSaveHandlerThread.quitSafely();
        } else {
            queueSaveHandlerThread.quit();
        }
        playback.release();
        playback = null;
        mediaSession.release();
    }

    public boolean isPlaying() {
        return playback != null && playback.isPlaying();
    }

    public int getPosition() {
        return position;
    }

    public void playNextSong(boolean force) {
        playSongAt(getNextPosition(force));
    }

    private boolean openTrackAndPrepareNextAt(int position) {
        synchronized (this) {
            this.position = position;
            boolean prepared = openCurrent();
            if (prepared) prepareNextImpl();
            notifyChange(META_CHANGED);
            notHandledMetaChangedForCurrentTrack = false;
            return prepared;
        }
    }

    private boolean openCurrent() {
        synchronized (this) {
            try {
                return playback.setDataSource(getTrackUri(getCurrentSong()));
            } catch (Exception e) {
                return false;
            }
        }
    }

    private void prepareNext() {
        playerHandler.removeMessages(PREPARE_NEXT);
        playerHandler.obtainMessage(PREPARE_NEXT).sendToTarget();
    }

    private boolean prepareNextImpl() {
        synchronized (this) {
            try {
                int nextPosition = getNextPosition(false);
                playback.setNextDataSource(getTrackUri(getSongAt(nextPosition)));
                this.nextPosition = nextPosition;
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }

    private void closeAudioEffectSession() {
        final Intent audioEffectsIntent = new Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
        audioEffectsIntent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, playback.getAudioSessionId());
        audioEffectsIntent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, getPackageName());
        sendBroadcast(audioEffectsIntent);
    }

    private boolean requestFocus() {
        return (getAudioManager().requestAudioFocus(audioFocusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
    }

    public void initNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !PreferenceUtil.getInstance(this).classicNotification()) {
            playingNotification = new PlayingNotificationImpl24();
        } else {
            playingNotification = new PlayingNotificationImpl();
        }
        playingNotification.init(this);
    }

    public void updateNotification() {
        if (playingNotification != null && getCurrentSong().id != -1) {
            playingNotification.update();
        }
    }

    private void updateMediaSessionPlaybackState() {
        mediaSession.setPlaybackState(
                new PlaybackStateCompat.Builder()
                        .setActions(MEDIA_SESSION_ACTIONS)
                        .setState(isPlaying() ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED,
                                getPosition(), 1)
                        .build());
    }

    private void updateMediaSessionMetaData() {
        final Song song = getCurrentSong();

        if (song.id == -1) {
            mediaSession.setMetadata(null);
            return;
        }

        final MediaMetadataCompat.Builder metaData = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artistName)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, song.artistName)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.albumName)
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, song.duration)
                .putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, getPosition() + 1)
                .putLong(MediaMetadataCompat.METADATA_KEY_YEAR, song.year)
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, null);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            metaData.putLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, getPlayingQueue().size());
        }

        if (PreferenceUtil.getInstance(this).albumArtOnLockscreen()) {
            final Point screenSize = Util.getScreenSize(MusicService.this);
            final BitmapRequestBuilder<?, Bitmap> request = SongGlideRequest.Builder.from(Glide.with(MusicService.this), song)
                    .checkIgnoreMediaStore(MusicService.this)
                    .asBitmap().build();
            if (PreferenceUtil.getInstance(this).blurredAlbumArt()) {
                request.transform(new BlurTransformation.Builder(MusicService.this).build());
            }
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    request.into(new SimpleTarget<Bitmap>(screenSize.x, screenSize.y) {
                        @Override
                        public void onLoadFailed(Exception e, Drawable errorDrawable) {
                            super.onLoadFailed(e, errorDrawable);
                            mediaSession.setMetadata(metaData.build());
                        }

                        @Override
                        public void onResourceReady(Bitmap resource, GlideAnimation<? super Bitmap> glideAnimation) {
                            metaData.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, copy(resource));
                            mediaSession.setMetadata(metaData.build());
                        }
                    });
                }
            });
        } else {
            mediaSession.setMetadata(metaData.build());
        }
    }

    private static Bitmap copy(Bitmap bitmap) {
        Bitmap.Config config = bitmap.getConfig();
        if (config == null) {
            config = Bitmap.Config.RGB_565;
        }
        try {
            return bitmap.copy(config, false);
        } catch (OutOfMemoryError e) {
            e.printStackTrace();
            return null;
        }
    }

    public void runOnUiThread(Runnable runnable) {
        uiThreadHandler.post(runnable);
    }

    public Song getCurrentSong() {
        return getSongAt(getPosition());
    }

    public Song getSongAt(int position) {
        if (position >= 0 && position < getPlayingQueue().size()) {
            return getPlayingQueue().get(position);
        } else {
            return Song.EMPTY_SONG;
        }
    }

    public int getNextPosition(boolean force) {
        int position = getPosition() + 1;
        switch (getRepeatMode()) {
            case REPEAT_MODE_ALL:
                if (isLastTrack()) {
                    position = 0;
                }
                break;
            case REPEAT_MODE_THIS:
                if (force) {
                    if (isLastTrack()) {
                        position = 0;
                    }
                } else {
                    position -= 1;
                }
                break;
            default:
            case REPEAT_MODE_NONE:
                if (isLastTrack()) {
                    position -= 1;
                }
                break;
        }
        return position;
    }

    private boolean isLastTrack() {
        return getPosition() == getPlayingQueue().size() - 1;
    }

    public ArrayList<Song> getPlayingQueue() {
        return playingQueue;
    }

    public int getRepeatMode() {
        return repeatMode;
    }

    public void setRepeatMode(final int repeatMode) {
        switch (repeatMode) {
            case REPEAT_MODE_NONE:
            case REPEAT_MODE_ALL:
            case REPEAT_MODE_THIS:
                this.repeatMode = repeatMode;
                PreferenceManager.getDefaultSharedPreferences(this).edit()
                        .putInt(SAVED_REPEAT_MODE, repeatMode)
                        .apply();
                prepareNext();
                handleAndSendChangeInternal(REPEAT_MODE_CHANGED);
                break;
        }
    }

    public void openQueue(@Nullable final ArrayList<Song> playingQueue, final int startPosition, final boolean startPlaying) {
        if (playingQueue != null && !playingQueue.isEmpty() && startPosition >= 0 && startPosition < playingQueue.size()) {
            // it is important to copy the playing queue here first as we might add/remove songs later
            originalPlayingQueue = new ArrayList<>(playingQueue);
            this.playingQueue = new ArrayList<>(originalPlayingQueue);

            int position = startPosition;
            if (shuffleMode == SHUFFLE_MODE_SHUFFLE) {
                ShuffleHelper.makeShuffleList(this.playingQueue, startPosition);
                position = 0;
            }
            if (startPlaying) {
                playSongAt(position);
            } else {
                setPosition(position);
            }
            notifyChange(QUEUE_CHANGED);
        }
    }

    public void addSong(int position, Song song) {
        playingQueue.add(position, song);
        originalPlayingQueue.add(position, song);
        notifyChange(QUEUE_CHANGED);
    }

    public void addSong(Song song) {
        playingQueue.add(song);
        originalPlayingQueue.add(song);
        notifyChange(QUEUE_CHANGED);
    }

    public void addSongs(int position, List<Song> songs) {
        playingQueue.addAll(position, songs);
        originalPlayingQueue.addAll(position, songs);
        notifyChange(QUEUE_CHANGED);
    }

    public void addSongs(List<Song> songs) {
        playingQueue.addAll(songs);
        originalPlayingQueue.addAll(songs);
        notifyChange(QUEUE_CHANGED);
    }

    public void removeSong(int position) {
        if (getShuffleMode() == SHUFFLE_MODE_NONE) {
            playingQueue.remove(position);
            originalPlayingQueue.remove(position);
        } else {
            originalPlayingQueue.remove(playingQueue.remove(position));
        }

        rePosition(position);

        notifyChange(QUEUE_CHANGED);
    }

    public void removeSong(@NonNull Song song) {
        for (int i = 0; i < playingQueue.size(); i++) {
            if (playingQueue.get(i).id == song.id) {
                playingQueue.remove(i);
                rePosition(i);
            }
        }
        for (int i = 0; i < originalPlayingQueue.size(); i++) {
            if (originalPlayingQueue.get(i).id == song.id) {
                originalPlayingQueue.remove(i);
            }
        }
        notifyChange(QUEUE_CHANGED);
    }

    private void rePosition(int deletedPosition) {
        int currentPosition = getPosition();
        if (deletedPosition < currentPosition) {
            position = currentPosition - 1;
        } else if (deletedPosition == currentPosition) {
            if (playingQueue.size() > deletedPosition) {
                setPosition(position);
            } else {
                setPosition(position - 1);
            }
        }
    }

    public void moveSong(int from, int to) {
        if (from == to) return;
        final int currentPosition = getPosition();
        Song songToMove = playingQueue.remove(from);
        playingQueue.add(to, songToMove);
        if (getShuffleMode() == SHUFFLE_MODE_NONE) {
            Song tmpSong = originalPlayingQueue.remove(from);
            originalPlayingQueue.add(to, tmpSong);
        }
        if (from > currentPosition && to <= currentPosition) {
            position = currentPosition + 1;
        } else if (from < currentPosition && to >= currentPosition) {
            position = currentPosition - 1;
        } else if (from == currentPosition) {
            position = to;
        }
        notifyChange(QUEUE_CHANGED);
    }

    public void clearQueue() {
        playingQueue.clear();
        originalPlayingQueue.clear();

        setPosition(-1);
        notifyChange(QUEUE_CHANGED);
    }

    public void playSongAt(final int position) {
        // handle this on the handlers thread to avoid blocking the ui thread
        playerHandler.removeMessages(PLAY_SONG);
        playerHandler.obtainMessage(PLAY_SONG, position, 0).sendToTarget();
    }

    public void setPosition(final int position) {
        // handle this on the handlers thread to avoid blocking the ui thread
        playerHandler.removeMessages(SET_POSITION);
        playerHandler.obtainMessage(SET_POSITION, position, 0).sendToTarget();
    }

    private void playSongAtImpl(int position) {
        if (openTrackAndPrepareNextAt(position)) {
            play();
        } else {
            Toast.makeText(this, getResources().getString(R.string.unplayable_file), Toast.LENGTH_SHORT).show();
        }
    }

    public void pause() {
        pausedByTransientLossOfFocus = false;
        if (playback.isPlaying()) {
            playback.pause();
            notifyChange(PLAY_STATE_CHANGED);
        }
    }

    public void play() {
        synchronized (this) {
            if (requestFocus()) {
                if (!playback.isPlaying()) {
                    if (!playback.isInitialized()) {
                        playSongAt(getPosition());
                    } else {
                        playback.start();
                        if (!becomingNoisyReceiverRegistered) {
                            registerReceiver(becomingNoisyReceiver, becomingNoisyReceiverIntentFilter);
                            becomingNoisyReceiverRegistered = true;
                        }
                        if (notHandledMetaChangedForCurrentTrack) {
                            handleChangeInternal(META_CHANGED);
                            notHandledMetaChangedForCurrentTrack = false;
                        }
                        notifyChange(PLAY_STATE_CHANGED);

                        // fixes a bug where the volume would stay ducked because the AudioManager.AUDIOFOCUS_GAIN event is not sent
                        playerHandler.removeMessages(DUCK);
                        playerHandler.sendEmptyMessage(UNDUCK);
                    }
                }
            } else {
                Toast.makeText(this, getResources().getString(R.string.audio_focus_denied), Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void playSongs(ArrayList<Song> songs, int shuffleMode) {
        if (songs != null && !songs.isEmpty()) {
            if (shuffleMode == SHUFFLE_MODE_SHUFFLE) {
                int startPosition = 0;
                if (!songs.isEmpty()) {
                    startPosition = new Random().nextInt(songs.size());
                }
                openQueue(songs, startPosition, false);
                setShuffleMode(shuffleMode);
            } else {
                openQueue(songs, 0, false);
            }
            play();
        } else {
            Toast.makeText(getApplicationContext(), R.string.playlist_is_empty, Toast.LENGTH_LONG).show();
        }
    }

    public void playPreviousSong(boolean force) {
        playSongAt(getPreviousPosition(force));
    }

    public void back(boolean force) {
        if (getSongProgressMillis() > 2000) {
            seek(0);
        } else {
            playPreviousSong(force);
        }
    }

    public int getPreviousPosition(boolean force) {
        int newPosition = getPosition() - 1;
        switch (repeatMode) {
            case REPEAT_MODE_ALL:
                if (newPosition < 0) {
                    newPosition = getPlayingQueue().size() - 1;
                }
                break;
            case REPEAT_MODE_THIS:
                if (force) {
                    if (newPosition < 0) {
                        newPosition = getPlayingQueue().size() - 1;
                    }
                } else {
                    newPosition = getPosition();
                }
                break;
            default:
            case REPEAT_MODE_NONE:
                if (newPosition < 0) {
                    newPosition = 0;
                }
                break;
        }
        return newPosition;
    }

    public int getSongProgressMillis() {
        return playback.position();
    }

    public int getSongDurationMillis() {
        return playback.duration();
    }

    public long getQueueDurationMillis(int position) {
        long duration = 0;
        for (int i = position + 1; i < playingQueue.size(); i++)
            duration += playingQueue.get(i).duration;
        return duration;
    }

    public int seek(int millis) {
        synchronized (this) {
            try {
                int newPosition = playback.seek(millis);
                throttledSeekHandler.notifySeek();
                return newPosition;
            } catch (Exception e) {
                return -1;
            }
        }
    }

    public void cycleRepeatMode() {
        switch (getRepeatMode()) {
            case REPEAT_MODE_NONE:
                setRepeatMode(REPEAT_MODE_ALL);
                break;
            case REPEAT_MODE_ALL:
                setRepeatMode(REPEAT_MODE_THIS);
                break;
            default:
                setRepeatMode(REPEAT_MODE_NONE);
                break;
        }
    }

    public void toggleShuffle() {
        if (getShuffleMode() == SHUFFLE_MODE_NONE) {
            setShuffleMode(SHUFFLE_MODE_SHUFFLE);
        } else {
            setShuffleMode(SHUFFLE_MODE_NONE);
        }
    }

    public int getShuffleMode() {
        return shuffleMode;
    }

    public void setShuffleMode(final int shuffleMode) {
        PreferenceManager.getDefaultSharedPreferences(this).edit()
                .putInt(SAVED_SHUFFLE_MODE, shuffleMode)
                .apply();
        switch (shuffleMode) {
            case SHUFFLE_MODE_SHUFFLE:
                this.shuffleMode = shuffleMode;
                ShuffleHelper.makeShuffleList(this.getPlayingQueue(), getPosition());
                position = 0;
                break;
            case SHUFFLE_MODE_NONE:
                this.shuffleMode = shuffleMode;
                int currentSongId = getCurrentSong().id;
                playingQueue = new ArrayList<>(originalPlayingQueue);
                int newPosition = 0;
                for (Song song : getPlayingQueue()) {
                    if (song.id == currentSongId) {
                        newPosition = getPlayingQueue().indexOf(song);
                    }
                }
                position = newPosition;
                break;
        }
        handleAndSendChangeInternal(SHUFFLE_MODE_CHANGED);
        notifyChange(QUEUE_CHANGED);
    }

    private void notifyChange(@NonNull final String what) {
        handleAndSendChangeInternal(what);
        sendPublicIntent(what);
    }

    private void handleAndSendChangeInternal(@NonNull final String what) {
        handleChangeInternal(what);
        sendChangeInternal(what);
    }

    //TODO: my mods
    Song current = null;

    public class SettingsContentObserver extends ContentObserver {
        int previousVolume;
        Context context;

        public SettingsContentObserver(Context c, Handler handler) {
            super(handler);
            context=c;

            AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            previousVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        }

        @Override
        public boolean deliverSelfNotifications() {
            return super.deliverSelfNotifications();
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);

            int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);

            int delta=previousVolume-currentVolume;

            if(delta != 0)
            {
                previousVolume=currentVolume;
                if (webSocketServer != null && webSocketServer.connections() != null && !webSocketServer.connections().isEmpty()) {
                    JSONObject jsonObject = new JSONObject();
                    try {
                        jsonObject.put("volume", currentVolume);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    String json = jsonObject.toString();
                    for (WebSocket webSocket : webSocketServer.connections()) {
                        webSocket.send(json);
                    }
                }
            }
        }
    }

    // to let other apps know whats playing. i.E. last.fm (scrobbling) or musixmatch
    private void sendPublicIntent(@NonNull final String what) {
        final Song song = getCurrentSong();

        final Intent intent = new Intent(what.replace(PHONOGRAPH_PACKAGE_NAME, MUSIC_PACKAGE_NAME));

        intent.putExtra("id", song.id);

        intent.putExtra("artist", song.artistName);
        intent.putExtra("album", song.albumName);
        intent.putExtra("track", song.title);

        intent.putExtra("duration", song.duration);
        intent.putExtra("position", (long) getSongProgressMillis());

        intent.putExtra("playing", isPlaying());

        intent.putExtra("scrobbling_source", PHONOGRAPH_PACKAGE_NAME);

        sendStickyBroadcast(intent);
    }

    private void sendChangeInternal(final String what) {
        sendBroadcast(new Intent(what));
        appWidgetBig.notifyChange(this, what);
        appWidgetClassic.notifyChange(this, what);
        appWidgetSmall.notifyChange(this, what);
        appWidgetCard.notifyChange(this, what);

        //TODO: my mods
        final Song song = getCurrentSong();
        if (webSocketServer != null && webSocketServer.connections() != null && !webSocketServer.connections().isEmpty()) {
            if ((current != null && !current.title.equals(song.title)) || current == null) {
                String title = song.title;
                title = title.replaceAll("\\(.*?\\)", "");

                current = song;

                Log.d(TAG, "serve: title: "+title);
                String songName = "";
                String artist = "";
                if (title.contains("-")) {
                    String[] songRaw = title.split("-");
                    if (!title.replace(" ", "").contains("Ne-Yo")) {
                        songName = songRaw[1].trim();
                        artist = songRaw[0].trim();
                    } else {
                        songName = songRaw[2].trim();
                        artist = (songRaw[0] + "-" + songRaw[1]).trim();
                    }
                } else {
                    songName = title;
                }
                String state = "pause";
                if (isPlaying()) {
                    state = "play";
                }
                JSONObject jsonObject = new JSONObject();
                try {
                    jsonObject.put("state", state);
                    jsonObject.put("songName", songName);
                    jsonObject.put("songAirtist", artist);
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                String json = jsonObject.toString();

                for (WebSocket webSocket : webSocketServer.connections()) {
                    webSocket.send(json);
                }

            } else {
                String state = "pause";
                if (isPlaying()) {
                    state = "play";
                }
                JSONObject jsonObject = new JSONObject();
                try {
                    jsonObject.put("state", state);
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                String json = jsonObject.toString();

                for (WebSocket webSocket : webSocketServer.connections()) {
                    webSocket.send(json);
                }
            }
        }
    }

    private static final long MEDIA_SESSION_ACTIONS = PlaybackStateCompat.ACTION_PLAY
            | PlaybackStateCompat.ACTION_PAUSE
            | PlaybackStateCompat.ACTION_PLAY_PAUSE
            | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
            | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            | PlaybackStateCompat.ACTION_STOP
            | PlaybackStateCompat.ACTION_SEEK_TO;

    private void handleChangeInternal(@NonNull final String what) {
        switch (what) {
            case PLAY_STATE_CHANGED:
                updateNotification();
                updateMediaSessionPlaybackState();
                final boolean isPlaying = isPlaying();
                if (!isPlaying && getSongProgressMillis() > 0) {
                    savePositionInTrack();
                }
                songPlayCountHelper.notifyPlayStateChanged(isPlaying);
                break;
            case META_CHANGED:
                updateNotification();
                updateMediaSessionMetaData();
                savePosition();
                savePositionInTrack();
                final Song currentSong = getCurrentSong();
                HistoryStore.getInstance(this).addSongId(currentSong.id);
                if (songPlayCountHelper.shouldBumpPlayCount()) {
                    SongPlayCountStore.getInstance(this).bumpPlayCount(songPlayCountHelper.getSong().id);
                }
                songPlayCountHelper.notifySongChanged(currentSong);
                break;
            case QUEUE_CHANGED:
                updateMediaSessionMetaData(); // because playing queue size might have changed
                saveState();
                if (playingQueue.size() > 0) {
                    prepareNext();
                } else {
                    playingNotification.stop();
                }
                break;
        }
    }

    public int getAudioSessionId() {
        return playback.getAudioSessionId();
    }

    public MediaSessionCompat getMediaSession() {
        return mediaSession;
    }

    public void releaseWakeLock() {
        if (wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    public void acquireWakeLock(long milli) {
        wakeLock.acquire(milli);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        switch (key) {
            case PreferenceUtil.GAPLESS_PLAYBACK:
                if (sharedPreferences.getBoolean(key, false)) {
                    prepareNext();
                } else {
                    playback.setNextDataSource(null);
                }
                break;
            case PreferenceUtil.ALBUM_ART_ON_LOCKSCREEN:
            case PreferenceUtil.BLURRED_ALBUM_ART:
                updateMediaSessionMetaData();
                break;
            case PreferenceUtil.COLORED_NOTIFICATION:
                updateNotification();
                break;
            case PreferenceUtil.CLASSIC_NOTIFICATION:
                initNotification();
                updateNotification();
                break;
        }
    }

    @Override
    public void onTrackWentToNext() {
        playerHandler.sendEmptyMessage(TRACK_WENT_TO_NEXT);
    }

    @Override
    public void onTrackEnded() {
        acquireWakeLock(30000);
        playerHandler.sendEmptyMessage(TRACK_ENDED);
    }

    private static final class PlaybackHandler extends Handler {
        @NonNull
        private final WeakReference<MusicService> mService;
        private float currentDuckVolume = 1.0f;

        public PlaybackHandler(final MusicService service, @NonNull final Looper looper) {
            super(looper);
            mService = new WeakReference<>(service);
        }

        @Override
        public void handleMessage(@NonNull final Message msg) {
            final MusicService service = mService.get();
            if (service == null) {
                return;
            }

            switch (msg.what) {
                case DUCK:
                    if (PreferenceUtil.getInstance(service).audioDucking()) {
                        currentDuckVolume -= .05f;
                        if (currentDuckVolume > .2f) {
                            sendEmptyMessageDelayed(DUCK, 10);
                        } else {
                            currentDuckVolume = .2f;
                        }
                    } else {
                        currentDuckVolume = 1f;
                    }
                    service.playback.setVolume(currentDuckVolume);
                    break;

                case UNDUCK:
                    if (PreferenceUtil.getInstance(service).audioDucking()) {
                        currentDuckVolume += .03f;
                        if (currentDuckVolume < 1f) {
                            sendEmptyMessageDelayed(UNDUCK, 10);
                        } else {
                            currentDuckVolume = 1f;
                        }
                    } else {
                        currentDuckVolume = 1f;
                    }
                    service.playback.setVolume(currentDuckVolume);
                    break;

                case TRACK_WENT_TO_NEXT:
                    if (service.getRepeatMode() == REPEAT_MODE_NONE && service.isLastTrack()) {
                        service.pause();
                        service.seek(0);
                    } else {
                        service.position = service.nextPosition;
                        service.prepareNextImpl();
                        service.notifyChange(META_CHANGED);
                    }
                    break;

                case TRACK_ENDED:
                    if (service.getRepeatMode() == REPEAT_MODE_NONE && service.isLastTrack()) {
                        service.notifyChange(PLAY_STATE_CHANGED);
                        service.seek(0);
                    } else {
                        service.playNextSong(false);
                    }
                    sendEmptyMessage(RELEASE_WAKELOCK);
                    break;

                case RELEASE_WAKELOCK:
                    service.releaseWakeLock();
                    break;

                case PLAY_SONG:
                    service.playSongAtImpl(msg.arg1);
                    break;

                case SET_POSITION:
                    service.openTrackAndPrepareNextAt(msg.arg1);
                    service.notifyChange(PLAY_STATE_CHANGED);
                    break;

                case PREPARE_NEXT:
                    service.prepareNextImpl();
                    break;

                case RESTORE_QUEUES:
                    service.restoreQueuesAndPositionIfNecessary();
                    break;

                case FOCUS_CHANGE:
                    switch (msg.arg1) {
                        case AudioManager.AUDIOFOCUS_GAIN:
                            if (!service.isPlaying() && service.pausedByTransientLossOfFocus) {
                                service.play();
                                service.pausedByTransientLossOfFocus = false;
                            }
                            removeMessages(DUCK);
                            sendEmptyMessage(UNDUCK);
                            break;

                        case AudioManager.AUDIOFOCUS_LOSS:
                            // Lost focus for an unbounded amount of time: stop playback and release media playback
                            service.pause();
                            break;

                        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                            // Lost focus for a short time, but we have to stop
                            // playback. We don't release the media playback because playback
                            // is likely to resume
                            boolean wasPlaying = service.isPlaying();
                            service.pause();
                            service.pausedByTransientLossOfFocus = wasPlaying;
                            break;

                        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                            // Lost focus for a short time, but it's ok to keep playing
                            // at an attenuated level
                            removeMessages(UNDUCK);
                            sendEmptyMessage(DUCK);
                            break;
                    }
                    break;
            }
        }
    }

    public class MusicBinder extends Binder {
        @NonNull
        public MusicService getService() {
            return MusicService.this;
        }
    }

    private final BroadcastReceiver widgetIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final String command = intent.getStringExtra(EXTRA_APP_WIDGET_NAME);

            if (AppWidgetClassic.NAME.equals(command)) {
                final int[] ids = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS);
                appWidgetClassic.performUpdate(MusicService.this, ids);
            } else if (AppWidgetSmall.NAME.equals(command)) {
                final int[] ids = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS);
                appWidgetSmall.performUpdate(MusicService.this, ids);
            } else if (AppWidgetBig.NAME.equals(command)) {
                final int[] ids = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS);
                appWidgetBig.performUpdate(MusicService.this, ids);
            } else if (AppWidgetCard.NAME.equals(command)) {
                final int[] ids = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS);
                appWidgetCard.performUpdate(MusicService.this, ids);
            }
        }
    };

    private class MediaStoreObserver extends ContentObserver implements Runnable {
        // milliseconds to delay before calling refresh to aggregate events
        private static final long REFRESH_DELAY = 500;
        private Handler mHandler;

        public MediaStoreObserver(Handler handler) {
            super(handler);
            mHandler = handler;
        }

        @Override
        public void onChange(boolean selfChange) {
            // if a change is detected, remove any scheduled callback
            // then post a new one. This is intended to prevent closely
            // spaced events from generating multiple refresh calls
            mHandler.removeCallbacks(this);
            mHandler.postDelayed(this, REFRESH_DELAY);
        }

        @Override
        public void run() {
            // actually call refresh when the delayed callback fires
            // do not send a sticky broadcast here
            handleAndSendChangeInternal(MEDIA_STORE_CHANGED);
        }
    }

    private class ThrottledSeekHandler implements Runnable {
        // milliseconds to throttle before calling run() to aggregate events
        private static final long THROTTLE = 500;
        private Handler mHandler;

        public ThrottledSeekHandler(Handler handler) {
            mHandler = handler;
        }

        public void notifySeek() {
            mHandler.removeCallbacks(this);
            mHandler.postDelayed(this, THROTTLE);
        }

        @Override
        public void run() {
            savePositionInTrack();
            sendPublicIntent(PLAY_STATE_CHANGED); // for musixmatch synced lyrics
        }
    }

    private static class SongPlayCountHelper {
        public static final String TAG = SongPlayCountHelper.class.getSimpleName();

        private StopWatch stopWatch = new StopWatch();
        private Song song = Song.EMPTY_SONG;

        public Song getSong() {
            return song;
        }

        boolean shouldBumpPlayCount() {
            return song.duration * 0.5d < stopWatch.getElapsedTime();
        }

        void notifySongChanged(Song song) {
            synchronized (this) {
                stopWatch.reset();
                this.song = song;
            }
        }

        void notifyPlayStateChanged(boolean isPlaying) {
            synchronized (this) {
                if (isPlaying) {
                    stopWatch.start();
                } else {
                    stopWatch.pause();
                }
            }
        }
    }

    //TODO: my mods

    private static class DownloadTask extends AsyncTask<String, Integer, String> {

        private Context context;
        private String title;
        String videoURL;
        String downloadURL;
        private NotificationManager mNotifyManager;
        private NotificationCompat.Builder mBuilder;
        private int id = 1;

        public DownloadTask(Context context, String videoURL, String downloadURL) {
            this.context = context;
            this.videoURL = videoURL;
            this.downloadURL = downloadURL;
        }

        public String getText(String url) throws Exception {
            URL website = new URL(url);
            URLConnection connection = website.openConnection();
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(
                            connection.getInputStream()));

            StringBuilder response = new StringBuilder();
            String inputLine;

            while ((inputLine = in.readLine()) != null)
                response.append(inputLine);

            in.close();

            return response.toString();
        }

        @Override
        protected void onPreExecute() {
            mNotifyManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            mBuilder = new NotificationCompat.Builder(context, "Gramophone");
            mBuilder.setContentTitle("Download")
                    .setContentText("Preparing to download...")
                    .setSmallIcon(R.mipmap.ic_launcher);
            mBuilder.setProgress(100, 0, false);
            mNotifyManager.notify(id, mBuilder.build());
        }

        public String getTitleQuietly(String youtubeUrl) {
            try {
                if (youtubeUrl != null) {
                    return new JSONObject(getText("http://www.youtube.com/oembed?url=" +
                            youtubeUrl + "&format=json")).getString("title");
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String s) {
            MediaScannerConnection.scanFile(context, new String[]{Environment.getExternalStorageDirectory()+"/Music/"+title+".m4a"}, new String[]{"audio/mp4"}, new MediaScannerConnection.OnScanCompletedListener() {
                @Override
                public void onScanCompleted(String path, Uri uri) {
                    mBuilder.setContentText("YouTube music "+ title + " downloaded successfully!");
                    // Removes the progress bar
                    mBuilder.setProgress(0, 0, false);
                    mNotifyManager.notify(id, mBuilder.build());
                }
            });
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            mBuilder.setContentText("Downloading " + title);
            mBuilder.setProgress(100, values[0], false);
            mNotifyManager.notify(id, mBuilder.build());
        }

        @Override
        protected String doInBackground(String... sUrl) {
            title = getTitleQuietly(videoURL);
            InputStream input = null;
            OutputStream output = null;
            HttpURLConnection connection = null;
            try {
                URL url = new URL(downloadURL);
                connection = (HttpURLConnection) url.openConnection();
                connection.connect();

                // expect HTTP 200 OK, so we don't mistakenly save error report
                // instead of the file
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    return "Server returned HTTP " + connection.getResponseCode()
                            + " " + connection.getResponseMessage();
                }

                // this will be useful to display download percentage
                // might be -1: server did not report the length
                int fileLength = connection.getContentLength();

                // download the file
                input = connection.getInputStream();
                output = new FileOutputStream(Environment.getExternalStorageDirectory()+"/Music/"+title+".m4a");

                byte data[] = new byte[4096];
                long total = 0;
                int count;
                while ((count = input.read(data)) != -1) {
                    // allow canceling with back button
                    if (isCancelled()) {
                        input.close();
                        return null;
                    }
                    total += count;
                    // publishing the progress....
                    if (fileLength > 0) // only if total length is known
                        publishProgress((int) (total * 100 / fileLength));
                    output.write(data, 0, count);
                }
            } catch (Exception e) {
                return e.toString();
            } finally {
                try {
                    if (output != null)
                        output.close();
                    if (input != null)
                        input.close();
                } catch (IOException ignored) {
                }

                if (connection != null)
                    connection.disconnect();
            }
            return null;
        }
    }


    @SuppressLint("StaticFieldLeak")
    public void downloadVideoYT(final String videoURL) {
        if (videoURL != null) {
            new YouTubeExtractor(this) {
                @Override
                public void onExtractionComplete(SparseArray<YtFile> ytFiles, VideoMeta vMeta) {
                    if (ytFiles != null) {
                        int itag = 140;
                        String downloadUrl = ytFiles.get(itag).getUrl();
                        new DownloadTask(MusicService.this.getApplicationContext(), videoURL, downloadUrl).execute();
                    }
                }
            }.extract(videoURL, true, false);
        }
    }

    public String indexHTML = "";
    public List<String> assets = null;

    static class getIndex extends AsyncTask<MusicService,Void,Void> {

        @SuppressLint("StaticFieldLeak")
        MusicService service = null;
        List<String> assets = new ArrayList<>();

        @Override
        protected Void doInBackground(final MusicService... services) {
            service = services[0];
            try {
                for (String file : service.getAssets().list("server")) {
                    if (file.equals("index.html")) {
                        InputStream json = service.getAssets().open("server/" + file);
                        BufferedReader r = new BufferedReader(new InputStreamReader(json));
                        StringBuilder total = new StringBuilder(json.available());
                        String line;
                        while ((line = r.readLine()) != null) {
                            total.append(line).append('\n');
                        }
                        service.indexHTML = total.toString();
                    } else {
                        assets.add(file);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void s) {
            service.assets = assets;
            try {
                service.androidWebServer.start();
                Log.d(TAG, "AndroidWebServer: start");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private WebSocketServer webSocketServer;
    private void createWebSocketClient() {
        if (webSocketServer != null) {
            try {
                webSocketServer.stop();
            } catch (Exception e) {
                e.printStackTrace();
            }
            webSocketServer = null;
        }
        final WifiApManager wifiApManager = new WifiApManager(MusicService.this);
        ip = null;
        InetAddress address = null;
        if (wifiApManager.isWifiConnected()) {
            address = wifiApManager.wifiIpAddress();
            ip = address.getHostAddress();
        } else if (wifiApManager.isWifiApEnabled()) {
            ip = "192.168.43.1";
            try {
                address = InetAddress.getByName("192.168.43.1");
            } catch (UnknownHostException e) {
                e.printStackTrace();
                try {
                    address = InetAddress.getLocalHost();
                } catch (UnknownHostException e1) {
                    e1.printStackTrace();
                }
            }
        } else if (wifiApManager.isWifiEnabled() || wifiApManager.isWifiEnabling()) {
            final boolean[] wait = {false};
            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (wifiApManager.isWifiEnabled() && wifiApManager.isWifiConnected()) {
                        wait[0] = true;
                    }
                }
            };
            IntentFilter filter = new IntentFilter();
            filter.addAction("android.net.wifi.STATE_CHANGE");
            filter.addAction("android.net.conn.CONNECTIVITY_CHANGE");
            registerReceiver(receiver, filter);
            while (wait[0]) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            unregisterReceiver(receiver);
            Log.d(TAG, "createWebSocketClient: IP");
            address = wifiApManager.wifiIpAddress();
            ip = address.getHostAddress();
        }
        if (ip == null) {
            return;
        }
        WebSocketImpl.DEBUG = true;
        webSocketServer = new WebSocketServer(new InetSocketAddress(address,8090 )) {
            @Override
            public void onOpen(WebSocket conn, ClientHandshake handshake) {
                int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                Song song = getCurrentSong();
                String title = song.title;
                title = title.replaceAll("\\(.*?\\)", "");

                current = song;

                Log.d(TAG, "serve: title: "+title);
                String songName = "";
                String artist = "";
                if (title.contains("-")) {
                    String[] songRaw = title.split("-");
                    if (!title.replace(" ", "").contains("Ne-Yo")) {
                        songName = songRaw[1].trim();
                        artist = songRaw[0].trim();
                    } else {
                        songName = songRaw[2].trim();
                        artist = (songRaw[0] + "-" + songRaw[1]).trim();
                    }
                } else {
                    songName = title;
                }
                String state = "pause";
                if (isPlaying()) {
                    state = "play";
                }

                JSONObject jsonObject = new JSONObject();
                try {
                    jsonObject.put("volume", currentVolume);
                    jsonObject.put("state", state);
                    jsonObject.put("songName", songName);
                    jsonObject.put("songAirtist", artist);
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                if (webSocketServer != null) {
                    conn.send(jsonObject.toString());
                }
            }

            @Override
            public void onClose(WebSocket conn, int code, String reason, boolean remote) {

            }

            @Override
            public void onMessage(WebSocket conn, String message) {

            }

            @Override
            public void onError(WebSocket conn, Exception ex) {
                Log.d(TAG, "onError: "+ex + " ex message: "+ex.getMessage());
            }

            @Override
            public void onStart() {

            }
        };
        webSocketServer.start();
    }

    @SuppressWarnings("deprecation")
    public class AndroidWebServer extends NanoHTTPD {

        AndroidWebServer(int port) {
            super(port);
            createWebSocketClient();
            SettingsContentObserver mSettingsContentObserver = new SettingsContentObserver(MusicService.this, new Handler());
            getApplicationContext().getContentResolver().registerContentObserver(android.provider.Settings.System.CONTENT_URI, true, mSettingsContentObserver );
        }

        @Override
        public Response serve(IHTTPSession session) {
            String msg = "";
            Map<String, String> parms = session.getParms();
            if (parms.get("action") != null) {
                Log.d(TAG, "serve: action: "+parms.get("action"));
                int secs10 = 10000;
                switch (parms.get("action")) {
                    case "volume":
                        String value = parms.get("value");
                        Log.d(TAG, "serve: vol: "+value);
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, Integer.parseInt(value), 0);
                        break;
                    case "pause":
                        pause();
                        break;
                    case "play":
                        play();
                        break;
                    case "next":
                        int song = getNextPosition(true);
                        playSongAt(song);
                        break;
                    case "prev":
                        song = getPreviousPosition(true);
                        playSongAt(song);
                        break;
                    case "yt":
                        value = parms.get("value");
                        Intent intent = new Intent(getPackageName()+".controlService");
                        intent.putExtra("action", "download");
                        intent.putExtra("video", value);
                        sendBroadcast(intent);
                        break;
                    case "offline":
                        (new Handler()).postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                Intent intent = new Intent(getPackageName()+".controlService");
                                intent.putExtra("action", "online");
                                intent.putExtra("value", false);
                                sendBroadcast(intent);
                            }
                        }, 500);
                        break;
                    case "online":
                        (new Handler()).postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                Intent intent = new Intent(getPackageName()+".controlService");
                                intent.putExtra("action", "online");
                                intent.putExtra("value", true);
                                sendBroadcast(intent);
                            }
                        }, 500);
                        break;
                    case "back10":
                        int currentMillis = playback.position();
                        if (currentMillis - secs10 > 0) {
                            seek(currentMillis - secs10);
                        } else {
                            seek(0);
                        }
                        break;
                    case "skip10":
                        currentMillis = playback.position();
                        if (currentMillis + secs10 < playback.duration()) {
                            seek(currentMillis + secs10);
                        } else {
                            song = getNextPosition(true);
                            playSongAt(song);
                        }
                        break;
                }
                if (msg.isEmpty()) {
                    msg = "success";
                }
            } else {
                String uri = session.getUri().substring(1);
                Log.d(TAG, "serve: uri: "+uri);
                if (uri.contains("index.html") || uri.replace("/", "").isEmpty()) {
                    msg = indexHTML.replace("max=\"100\"", "max=\""+max+"\"");
                } else if (assets.contains(uri)) {
                    String extension = uri.substring(uri.lastIndexOf(".")+1);
                    String type = NanoHTTPD.mimeTypes().get(extension);
                    try {
                        InputStream is = getAssets().open("server/"+uri);
                        Log.d(TAG, "serve: uri: "+uri+" mime: "+type+" is available: "+is.available() + " ext: "+extension);
                        return newFixedLengthResponse(Response.Status.OK, type, is, is.available());
                    } catch (IOException e) {
                        e.printStackTrace();
                        return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "ERROR");
                    }
                }
            }
            if (!msg.isEmpty()) {
                return newFixedLengthResponse(msg);
            }
            return newFixedLengthResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "ERROR");
        }
    }
}

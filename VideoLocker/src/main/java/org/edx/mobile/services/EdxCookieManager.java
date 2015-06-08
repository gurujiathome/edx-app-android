package org.edx.mobile.services;

import android.content.Context;
import android.os.Build;
import android.webkit.ValueCallback;

import org.apache.http.cookie.Cookie;
import org.edx.mobile.base.MainApplication;
import org.edx.mobile.event.SessionIdRefreshEvent;
import org.edx.mobile.logger.Logger;
import org.edx.mobile.module.prefs.PrefManager;
import org.edx.mobile.task.GetSessesionExchangeCookieTask;

import java.io.File;
import java.util.List;

import de.greenrobot.event.EventBus;

/**
 *  A central place for course data model transformation
 */
public class EdxCookieManager {

    protected final Logger logger = new Logger(getClass().getName());

    private static EdxCookieManager instance;

    private GetSessesionExchangeCookieTask task;

    public static synchronized EdxCookieManager getSharedInstance(){
        if ( instance == null )
            instance = new EdxCookieManager();
        return instance;
    }

    public void clearWebWiewCookie(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            android.webkit.CookieManager.getInstance().removeAllCookies(new ValueCallback() {
                @Override
                public void onReceiveValue(Object value) {
                    //do nothing?
                }
            });
        } else {
            android.webkit.CookieManager.getInstance().removeAllCookie();
        }
        PrefManager pref = new PrefManager(MainApplication.instance(), PrefManager.Pref.LOGIN);
        pref.put(PrefManager.Key.AUTH_ASSESSMENT_SESSION_ID, "");
        //we can not get the expiration date from cookie, so just set it to expire for one day
        pref.put(PrefManager.Key.AUTH_ASSESSMENT_SESSION_EXPIRATION, -1);

    }

    public void clearWebViewCache(Context context){
        try {
            clearWebWiewCookie();

            boolean success = context.deleteDatabase("webview.db");
            logger.debug("delete webview.db result = " + success);
            success =  context.deleteDatabase("webviewCache.db");
            logger.debug("delete webviewCache.db result = " + success);
        } catch (Exception e) {
            logger.error(e);
        }


        File webviewCacheDir = new File(context.getCacheDir().getAbsolutePath()+"/webviewCache");
        if(webviewCacheDir.exists()){
            context.deleteFile(webviewCacheDir.getAbsolutePath());
        }

        File appCacheDir = new File(context.getFilesDir().getAbsolutePath()+ "/webcache");
        if(appCacheDir.exists()){
            context.deleteFile(appCacheDir.getAbsolutePath());
        }
    }

    public synchronized  void tryToRefreshSessionCookie( ){
        if ( task == null || task.isCancelled()  ) {
            task =new GetSessesionExchangeCookieTask(MainApplication.instance()) {
                @Override
                public void onFinish(List<Cookie> result) {
                    if (result == null || result.isEmpty()) {
                        logger.debug("result is empty");
                        EventBus.getDefault().post(new SessionIdRefreshEvent(false));
                        return;
                    }
                    for (Cookie cookie : result) {
                        if (cookie.getName().equals(PrefManager.Key.SESSION_ID)) {
                            clearWebWiewCookie();
                            PrefManager pref = new PrefManager(MainApplication.instance(), PrefManager.Pref.LOGIN);
                            pref.put(PrefManager.Key.AUTH_ASSESSMENT_SESSION_ID, cookie.getValue());
                             pref.put(PrefManager.Key.AUTH_ASSESSMENT_SESSION_EXPIRATION, cookie.getExpiryDate() == null ? 0 : cookie.getExpiryDate().getTime());
                             EventBus.getDefault().post(new SessionIdRefreshEvent(true));
                            break;
                        }
                    }
                    task = null;
                }

                @Override
                public void onException(Exception ex) {
                    logger.error(ex);
                    EventBus.getDefault().post(new SessionIdRefreshEvent(false));
                    task = null;
                }
            };
            task.execute();
        }
    }
}

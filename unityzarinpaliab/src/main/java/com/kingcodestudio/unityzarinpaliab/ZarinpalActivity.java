package com.kingcodestudio.unityzarinpaliab;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import com.unity3d.player.UnityPlayer;
import com.zarinpal.ewallets.purchase.OnCallbackRequestPaymentListener;
import com.zarinpal.ewallets.purchase.OnCallbackVerificationPaymentListener;
import com.zarinpal.ewallets.purchase.PaymentRequest;
import com.zarinpal.ewallets.purchase.ZarinPal;

public class ZarinpalActivity extends Activity {

    // Unity context.
    private static final String UNITY_GAME_OBJECT_NAME = "ZarinpalAndroid";

    private static String m_merchantID;
    private static String m_callbackScheme;
    private static boolean m_verifyPurchase;
    // Singleton instance.
    public static ZarinpalActivity m_instance;

    //Purchase parameters
    private static long m_amount;
    private static String m_description;
    private static String m_title;


    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // If we've received a touch notification that the user has touched
        // outside the app, finish the activity.
        if (MotionEvent.ACTION_OUTSIDE == event.getAction()) {
            cancelPurchase();
            return true;
        }

        // Delegate everything else to Activity.
        return super.onTouchEvent(event);
    }

    private void ResizeActivity(){
        WindowManager.LayoutParams params = getWindow().getAttributes();
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int width = displayMetrics.widthPixels;
        int height = displayMetrics.heightPixels;
        params.x = -20;
        params.y = -10;
        int orientation = getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            params.height = 3*height/4;
            params.width = 2 * width/3;
        } else {
            params.height = height/2;
            params.width = 3 * width/4;
        }

        this.getWindow().setAttributes(params);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH, WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);

        //Returning from payment gateway
        String action = getIntent().getAction();
        Log.d("Zarinpal","Action : "+action);
        if(action!=null && action.equals("android.intent.action.VIEW"))
        {
            Uri data = getIntent().getData();
            if(data==null)
            {
                Log.d("Zarinpal","zarinpal purchase returned null");
            }
            else
            {
                Log.d("Zarinpal","zarinpal purchase returned : "+data.toString());
                String status = data.getQueryParameter("Status");
                if(status!=null && status.equals("OK"))
                {
                    Log.d("Zarinpal","Status OK trying to verify purchase...");
                    String authority = data.getQueryParameter("Authority");
                    UnityPlayer.UnitySendMessage(UNITY_GAME_OBJECT_NAME,"OnPurchaseSucceed",authority);
                    if(m_verifyPurchase){
                        Log.d("Zarinpal","Starting to verify purchase cause autoVerify is set to true : ");
                        verifyPurchase(data);
                    }
                    else
                    {
                        Log.d("Zarinpal","Ignore verifying purchase cause autoVerify is set to false : ");
                        finish();//Finish purchase flow and return o unity
                    }
                }
                else
                {
                    Log.d("Zarinpal","purchase failed : "+status);
                    UnityPlayer.UnitySendMessage(UNITY_GAME_OBJECT_NAME,"OnPurchaseFailed","null");
                    finish();
                }
            }
        }
        //finish activity because we have no data to handle
        else
        {
            setContentView(R.layout.activity_zarinpal);
            TextView titleTextView = findViewById(R.id.textViewTitle);
            titleTextView.setText(m_title);
            Resources res = getResources();
            String amountCurrencyString = String.format("%,d", m_amount);
            String priceText = res.getString(R.string.priceText, amountCurrencyString);
            TextView priceTextView = findViewById(R.id.priceTextview);
            priceTextView.setText(priceText);
            final Button purchaseButton = findViewById(R.id.purchaseButton);
            final Button cancelButton = findViewById(R.id.cancelButton);
            purchaseButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    cancelButton.setEnabled(false);
                    purchaseButton.setEnabled(false);
                    purchase(m_amount,m_description);
                }
            });

            cancelButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    cancelPurchase();
                }
            });
        }

        ResizeActivity();
    }

    public static void initialize(String merchantID,boolean verifyPurchase,String callbackScheme){
        m_merchantID = merchantID; // Store merchant id
        m_verifyPurchase = verifyPurchase;//Should verify purchase after purchase completed
        m_callbackScheme = callbackScheme;//App Scheme used for zarinpal callback
        UnityPlayer.UnitySendMessage(UNITY_GAME_OBJECT_NAME,"OnStoreInitialized","store initialized");
    }

    private void cancelPurchase(){
        UnityPlayer.UnitySendMessage(UNITY_GAME_OBJECT_NAME,"OnPurchaseCanceled","cancel by user");
        finish();
    }


    public static void startPurchaseFlow(long amount,String title,String desc)
    {
        m_amount = amount;
        m_description = desc;
        m_title = title;
        Intent intent = new Intent(UnityPlayer.currentActivity,ZarinpalActivity.class);
        UnityPlayer.currentActivity.startActivity(intent);
    }



    private void purchase(long amount, String description){
        ZarinPal purchase = ZarinPal.getPurchase(UnityPlayer.currentActivity);
        PaymentRequest payment = ZarinPal.getPaymentRequest();
        payment.setMerchantID(m_merchantID);
        payment.setAmount(amount);
        payment.setDescription(description);
        payment.setCallbackURL(m_callbackScheme);
        Log.d("Zarinpal","Creating purchase object with callback : "+m_callbackScheme);

        purchase.startPayment(payment, new OnCallbackRequestPaymentListener() {
            @Override
            public void onCallbackResultPaymentRequest(int status, String authority, Uri paymentGatewayUri, Intent intent) {
                if(status==100){
                    Log.d("Zarinpal","Payment started");
                    UnityPlayer.UnitySendMessage(UNITY_GAME_OBJECT_NAME,"OnPurchaseStarted","null");
                    UnityPlayer.currentActivity.startActivity(intent);
                }
                else
                {
                    Log.d("Zarinpal","Payment failed to start");
                    UnityPlayer.UnitySendMessage(UNITY_GAME_OBJECT_NAME,"OnPurchaseFailedToStart","error on payment request");
                }
            }
        });
    }

    private void verifyPurchase(Uri data){
        Log.d("Zarinpal","Verifying purchase for : "+data.toString());
        UnityPlayer.UnitySendMessage(UNITY_GAME_OBJECT_NAME,"OnPaymentVerificationStarted",data.toString());
        ZarinPal.getPurchase(UnityPlayer.currentActivity).verificationPayment(data, new OnCallbackVerificationPaymentListener() {
            @Override
            public void onCallbackResultVerificationPayment(boolean isPaymentSuccess, String refID, PaymentRequest paymentRequest)
            {
                if(isPaymentSuccess)
                {
                    Log.d("Zarinpal","Payment verify success");
                    UnityPlayer.UnitySendMessage(UNITY_GAME_OBJECT_NAME,"OnPaymentVerificationSucceed",refID);
                }
                else {
                    Log.d("Zarinpal","Payment verify failed");
                    UnityPlayer.UnitySendMessage(UNITY_GAME_OBJECT_NAME,"OnPaymentVerificationFailed","purchase is not valid");
                }
                finish();
            }
        });
    }
}

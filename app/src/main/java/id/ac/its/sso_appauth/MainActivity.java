package id.ac.its.sso_appauth;

import androidx.annotation.LongDef;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import net.openid.appauth.AuthState;
import net.openid.appauth.AuthorizationException;
import net.openid.appauth.AuthorizationRequest;
import net.openid.appauth.AuthorizationResponse;
import net.openid.appauth.AuthorizationService;
import net.openid.appauth.AuthorizationServiceConfiguration;
import net.openid.appauth.ResponseTypeValues;
import net.openid.appauth.TokenRequest;
import net.openid.appauth.TokenResponse;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {
    Button button_login, button_logout;
    TextView textUsername;
    ImageView imgProfile;

    private int RC_AUTH = 100;
    AuthorizationService mAuthService;
    AuthStateManager mStateManager;
    AuthState mAuthState;

    private static String TAG = "appauthlog";
    public static String MY_CLIENT_ID = "BD13563E-6550-4A1D-9DE0-0A9BB1593D65";
    public static Uri MY_REDIRECT_URI = Uri.parse("id.ac.its.my.courier:/oauth2callback");


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        button_login = (Button)findViewById(R.id.button_login);
        button_logout = (Button)findViewById(R.id.button_logout);
        textUsername = (TextView)findViewById(R.id.textUsername);


        mAuthService = new AuthorizationService(this);
        //mStateManager = new AuthStateManager(this);
        mStateManager= AuthStateManager.getInstance(this);

        if(mStateManager.getCurrent().isAuthorized()){
            Log.d(TAG, "Done");
            button_login.setText("Sudah login");
            mStateManager.getCurrent().performActionWithFreshTokens(mAuthService, new AuthState.AuthStateAction() {
                @Override
                public void execute(@Nullable String accessToken, @Nullable String idToken, @Nullable AuthorizationException ex) {
                    if (accessToken!=null){
                        Log.d(TAG, "Access token " + accessToken);
                        new ProfileTask().execute(accessToken);
                    }
                    else {
                        Toast.makeText(MainActivity.this, "Sesi berakhir, harap melakukan login lagi", Toast.LENGTH_LONG).show();
                    }

                }
            });
        }

        button_login.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                doAuthorization();
            }
        });

        button_logout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                signOut();
            }
        });
    }

    private void doAuthorization() {
        AuthorizationServiceConfiguration serviceConfig =
                new AuthorizationServiceConfiguration(
                        Uri.parse("https://dev-my.its.ac.id/authorize"), // authorization endpoint
                        Uri.parse("https://dev-my.its.ac.id/token")// token endpoint
                );

        AuthorizationRequest.Builder authRequestBuilder =
                new AuthorizationRequest.Builder(
                        serviceConfig, // the authorization service configuration
                        MY_CLIENT_ID, // the client ID, typically pre-registered and static
                        ResponseTypeValues.CODE, // the response_type value: we want a code
                        MY_REDIRECT_URI); // the redirect URI to which the auth response is sent

        AuthorizationRequest authRequest = authRequestBuilder
                .setScope("profile openid")
                .build();

        AuthorizationService authService = new AuthorizationService(this);
        Intent authIntent = authService.getAuthorizationRequestIntent(authRequest);
        startActivityForResult(authIntent, RC_AUTH);
    }

    //Handle the authorization response
    @Override
    protected void onActivityResult(final int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_AUTH ) {
            AuthorizationResponse resp = AuthorizationResponse.fromIntent(data);
            AuthorizationException ex = AuthorizationException.fromIntent(data);
            // ... process the response or exception ...
            if (resp != null) {
                //mAuthService = new AuthorizationService(this);
                mStateManager.updateAfterAuthorization(resp,ex);

                //Exchanging the authorization code
                mAuthService.performTokenRequest(
                        resp.createTokenExchangeRequest(),
                        new AuthorizationService.TokenResponseCallback() {
                            @Override public void onTokenRequestCompleted(
                                    TokenResponse resp, AuthorizationException ex) {
                                if (resp != null) {

                                    mStateManager.updateAfterTokenResponse(resp,ex);
                                    Log.d(TAG, "Access token " + resp.accessToken);
                                    button_login.setText("Berhasil login");
                                    new ProfileTask().execute(resp.accessToken);
                                } else {
                                    // authorization failed, check ex for more details
                                }
                            }
                        });

            } else {
                // authorization failed, check ex for more details
            }

        } else {
            // ...
        }

        if (mStateManager.getCurrent().isAuthorized()){
            Log.d(TAG, "Done");
            button_login.setText("is authorized");
            mStateManager.getCurrent().performActionWithFreshTokens(mAuthService, new AuthState.AuthStateAction() {
                @Override
                public void execute(@Nullable String accessToken, @Nullable String idToken, @Nullable AuthorizationException ex) {
                    Log.d(TAG, "Access token " + accessToken);
                    new ProfileTask().execute(accessToken);
                }
            });
        }
    }

    static class ProfileTask extends AsyncTask<String, Void, JSONObject>{
        @Override
        protected JSONObject doInBackground(String... tokens) {
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    .url("https://dev-my.its.ac.id/userinfo")
                    .addHeader("Authorization", String.format("Bearer %s", tokens[0]))
                    .build();

            try {
                Response response = client.newCall(request).execute();
                String jsonBody = response.body().string();
                Log.d(TAG, String.format("User Info Response %s", jsonBody));
                return new JSONObject(jsonBody);
            } catch (Exception e) {
                // Handle API error here
                Log.w(TAG, e);
            }
            return null;
        }

        @Override
        protected void onPostExecute(JSONObject userInfo) {
            super.onPostExecute(userInfo);
            if(userInfo!=null){
                Log.d(TAG, "user info available: ");
                String phonenumber = userInfo.optString("preferred_username", null);
                Log.d(TAG, "user info name: " + phonenumber);
            }
            else {
                Log.d(TAG, "user info null ");
            }
        }
    }

    private void signOut() {
        // discard the authorization and token state, but retain the configuration and
        // dynamic client registration (if applicable), to save from retrieving them again.
        AuthState currentState = mStateManager.getCurrent();
        AuthState clearedState =
                new AuthState(currentState.getAuthorizationServiceConfiguration());
        if (currentState.getLastRegistrationResponse() != null) {
            clearedState.update(currentState.getLastRegistrationResponse());
        }
        mStateManager.replace(clearedState);

        Toast.makeText(MainActivity.this, "LOGOUT", Toast.LENGTH_LONG).show();
        button_login.setText("login");
        /*Intent mainIntent = new Intent(this, MainActivity2.class);
        mainIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(mainIntent);
        finish();*/
    }
}
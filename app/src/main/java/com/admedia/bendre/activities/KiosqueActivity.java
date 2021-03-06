package com.admedia.bendre.activities;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.constraint.ConstraintLayout;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.admedia.bendre.R;
import com.admedia.bendre.api.WordPressService;
import com.admedia.bendre.adapters.ProductsViewAdapter;
import com.admedia.bendre.model.woocommerce.Product;
import com.admedia.bendre.util.CachesUtil;
import com.admedia.bendre.util.EndpointConstants;
import com.admedia.bendre.util.MenuUtil;
import com.admedia.bendre.util.MessageUtil;
import com.admedia.bendre.util.NetworkUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

import static com.admedia.bendre.activities.PostDetailsActivity.POST_TYPE;
import static com.admedia.bendre.util.Constants.USE_CACHE_DATA;

public class KiosqueActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {
    private static final String wcUsername = "ck_4da722350d0e2961565d298f9e288e4e6a2f3a8c";
    private static final String wcPassword = "cs_38bcc73ed4aab303914ceafedb800b0d6be873b5";

    private ProgressBar mProgressBar;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private RecyclerView rvProducts;
    private boolean useCacheData;
    private Call<JsonArray> call;

    private List<Product> products;
    private boolean isLoading = false;
    private ProductsViewAdapter productsViewAdapter;
    private int totalPages;
    private int currentPage = 1;
    private boolean loadMore = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        init();
        initScrollListener();
        fetchData();
    }

    private void init() {
        setContentView(R.layout.activity_kiosque);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null)
        {
            getSupportActionBar().setTitle(getString(R.string.menu_bendrekan));
        }

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        MenuUtil.getInstance().setNavigationView(this, navigationView, drawer);

        mProgressBar = findViewById(R.id.progressbar);

        rvProducts = findViewById(R.id.products);
        useCacheData = getIntent().getBooleanExtra(USE_CACHE_DATA, true);

        mSwipeRefreshLayout = findViewById(R.id.products_swipe_container);
        mSwipeRefreshLayout.setColorSchemeResources(R.color.colorPrimary, R.color.colorPrimaryDark);
        mSwipeRefreshLayout.canChildScrollUp();
        mSwipeRefreshLayout.setOnRefreshListener(() -> {
            mSwipeRefreshLayout.setRefreshing(true);
            useCacheData = !NetworkUtil.isOnline(this);
            currentPage = 1;
            fetchData();
        });
    }

    private void initScrollListener() {
        rvProducts.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                GridLayoutManager layoutManager = (GridLayoutManager) recyclerView.getLayoutManager();
                if (!isLoading)
                {
                    if (layoutManager != null && layoutManager.findLastCompletelyVisibleItemPosition() == products.size() - 1)
                    {
                        if (currentPage + 1 <= totalPages)
                        {
                            currentPage++;
                            fetchData();
                            isLoading = true;
                            loadMore = true;
                        }
                    }
                }
            }
        });
    }

    private void fetchData() {
        showProgress(true);

        if (!useCacheData)
        {
            fetchDataFromRemote();
        }
        else
        {
            fetchDataFromCache();
        }
    }

    private void fetchDataFromCache() {
        try
        {
            Object data = CachesUtil.getInstance().readCachedFile(getApplicationContext(), "products");
            List array = Collections.singletonList(data);
            products = (List<Product>) array.get(0);
            fillData();
        }
        catch (IOException e)
        {
            e.printStackTrace();
            fetchDataFromRemote();
        }
        catch (ClassNotFoundException e)
        {
            e.printStackTrace();
            fetchDataFromRemote();
        }
    }

    private void fetchDataFromRemote() {
        if (NetworkUtil.isOnline(this))
        {
            new Thread(new ProductsRunnable()).start();
        }
        else
        {
            MessageUtil.getInstance().ToastMessage(getApplicationContext(), getString(R.string.no_internet_connexion));
            fetchDataFromCache();
        }
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START))
        {
            drawer.closeDrawer(GravityCompat.START);
        }
        else
        {
            Intent intent = new Intent(getApplicationContext(), PostsActivity.class);
            intent.putExtra(POST_TYPE, getString(R.string.menu_a_la_une));
            startActivity(intent);
//            super.onBackPressed();
        }
    }

    private void showProgress(boolean show) {
        if (!show)
        {
            if (mProgressBar != null)
            {
                mProgressBar.setVisibility(View.INVISIBLE);
            }
            if (mSwipeRefreshLayout != null)
            {
                mSwipeRefreshLayout.setRefreshing(false);
            }
        }
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        // Handle navigation view item clicks here.
        MenuUtil.getInstance().openPage(getApplicationContext(), item.getItemId());
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    private void fillData() {
        showProgress(false);
        isLoading = false;

        if (products.size() > 0)
        {
            try
            {
                CachesUtil.getInstance().createCachedFileForProducts(getApplicationContext(), "products", products);
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }

            if (!loadMore)
            {
                rvProducts.setLayoutManager(new GridLayoutManager(getApplicationContext(), 2));
                productsViewAdapter = new ProductsViewAdapter(this, products);
                rvProducts.setAdapter(productsViewAdapter);
            }
            else
            {
                productsViewAdapter.notifyItemRemoved(products.size());
                productsViewAdapter.notifyDataSetChanged();
            }
        }
        else
        {
            rvProducts.setVisibility(View.GONE);
            ConstraintLayout layout = findViewById(R.id.products_container);

            TextView textView = new TextView(getApplicationContext());
            textView.setText(getString(R.string.no_news_bendrekan));
            textView.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
            textView.setTextColor(getResources().getColor(R.color.black));
            textView.setTextSize(20);
            textView.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);
            RelativeLayout relativeLayout = new RelativeLayout(getApplicationContext());
            relativeLayout.addView(textView);
            layout.addView(relativeLayout);
        }

        showProgress(false);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (this.call != null)
        {
            this.call.cancel();
            this.call = null;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (this.call != null)
        {
            this.call.cancel();
            this.call = null;
        }
    }

    class ProductsRunnable implements Runnable {

        @Override
        public void run() {
            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(EndpointConstants.WC_BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                    .build();

            WordPressService apiService = retrofit.create(WordPressService.class);
            call = apiService.getProducts(wcUsername, wcPassword, "date", "desc", currentPage);
            call.enqueue(new Callback<JsonArray>() {
                @Override
                public void onResponse(@NonNull Call<JsonArray> call, @NonNull Response<JsonArray> response) {
                    if (!isLoading)
                    {
                        products = new ArrayList<>();
                    }

                    if (response.body() != null)
                    {
                        if (response.headers().get("x-wp-totalpages") != null)
                        {
                            totalPages = Integer.parseInt(Objects.requireNonNull(response.headers().get("x-wp-totalpages")));
                        }

                        JsonArray elements = response.body().getAsJsonArray();
                        for (int counter = 0; counter < elements.size(); counter++)
                        {
                            JsonElement element = elements.get(counter);
                            Product product = new Product((JsonObject) element);
                            products.add(product);
                        }
                    }
                    fillData();
                }

                @Override
                public void onFailure(@NonNull Call<JsonArray> call, @NonNull Throwable t) {
                    showProgress(false);
                    if (!call.isCanceled())
                    {
                        MessageUtil.getInstance().ToastMessage(getApplicationContext(), getString(R.string.cannot_fetch_data));
                    }
                }
            });
        }
    }
}

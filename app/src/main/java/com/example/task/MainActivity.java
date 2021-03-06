package com.example.task;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.example.task.arch.Task;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Date;
import java.util.List;


/**
 * This class displays a list of task in a RecyclerView. The task are saved in a Room database.
 * The layout for this activity also displays a FAB that allows users to start the
 * NewTaskActivity to add new tasks. Users can delete a task by swiping it away, or delete all
 * tasks through the Options menu. Whenever a new task is added, deleted, or updated,
 * the RecyclerView showing the list of tasks automatically updates.
 *
 */
public class MainActivity extends AppCompatActivity {

    // Intent request codes
    public static final int NEW_TASK_REQUEST_CODE = 1;
    public static final int UPDATE_TASK_REQUEST_CODE = 2;

    // Intent extended data string constants
    public static final String EXTRA_DATA_ID = "extra_data_id";
    public static final String EXTRA_DATA_UPDATE_TASK = "extra_task_to_be_updated";
    public static final String EXTRA_DATA_UPDATE_DETAILS = "extra_details_to_be_updated";
    public static final String EXTRA_DATA_UPDATE_DATE = "extra_date_to_be_updated";

    // Shared preferences string constants
    private static final String PREF_FILE = "shared_pref_file";
    private static final String VISIBILITY_KEY = "visibility";
    private boolean isItemVisible;

    // Member variables
    private FirebaseAuth mAuth;
    private TaskViewModel mViewModel;
    private SharedPreferences mSharedPrefs;
    private TaskListAdapter mAdapter;
    private CoordinatorLayout mCoordinatorLayout;
    private RecyclerView mRecyclerView;
    private Snackbar mPressAgainSnackBar;

    // Declared variable
    private long beforeExitTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.AppTheme_NoActionBar); // Set the base theme of this context.
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Get the instance of the view objects and capture them from the layout.
        mCoordinatorLayout = findViewById(R.id.coordinatorLayout);

        // Initialize the Firebase instance.
        mAuth = FirebaseAuth.getInstance();

        // Create the shared pref file.
        mSharedPrefs = getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);

        // Set up the custom toolbar.
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Set up the recycler view.
        mAdapter = new TaskListAdapter();
        mRecyclerView = findViewById(R.id.recyclerView);
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setAdapter(mAdapter);

        // Set up the view model.
        // Get all the tasks from the database and associate them to the adapter.
        mViewModel = new ViewModelProvider(this).get(TaskViewModel.class);
        mViewModel.getAllTasks().observe(this, new Observer<List<Task>>() {
            @Override
            public void onChanged(@Nullable List<Task> tasks) {
                mAdapter.submitList(tasks); // Submits a new list to be diffed, and displayed.
            }
        });

        // Add the functionality to swipe items in the RecyclerView to delete the swiped item.
        ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0,
                ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            // Will not implement the onMove() in this app.
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            // When the user swipes a task, delete that task from the database.
            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                Task myTask = mAdapter.getTaskAtPosition(position);
                mViewModel.delete(myTask); // Delete the task.
                // Tell user the task is completed.
                Snackbar snackbar = Snackbar.make(mCoordinatorLayout, R.string.task_completed,
                        Snackbar.LENGTH_LONG);
                snackbar.show();
            }
        });
        helper.attachToRecyclerView(mRecyclerView); // Attach the touch helper to recycler view.

        // The user can edit and update the task, when the item in the recycler view is clicked.
        mAdapter.setOnItemClickListener(new TaskListAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(Task task) {
               launchEditActivity(task);
            }
            // The user can also remove/complete the task when the radio button is click.
            @Override
            public void onDeleteClick(int position) {
                Task myTask = mAdapter.getTaskAtPosition(position);
                mViewModel.delete(myTask);
                // Tell user the task is completed.
                Snackbar snackbar = Snackbar.make(mCoordinatorLayout, R.string.task_completed,
                        Snackbar.LENGTH_LONG);
                snackbar.show();
            }
        });

        // Floating action button setup.
        FloatingActionButton mFab = findViewById(R.id.fab);
        mFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                createNewTask();
            }
        });
    }

    /**
     * Check firebase if there is a user currently logged in.
     */
    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null || !currentUser.isEmailVerified()) {
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();
        }
    }


    /**
     * Dispatch incoming result to the NewTaskActivity.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == NEW_TASK_REQUEST_CODE && resultCode == RESULT_OK) {
            if (data != null) {
                // Retrieve the extended data from the NewTaskActivity intent.
                String taskData = data.getStringExtra(NewTaskActivity.EXTRA_REPLY_TASK);
                String detailsData = data.getStringExtra(NewTaskActivity.EXTRA_REPLY_DETAILS);
                long date = data.getLongExtra(NewTaskActivity.EXTRA_REPLY_DATE, 0);
                Date dateData = new Date(date); // Convert the date long type to date type.

                // Insert new task to database.
                if (date != 0) {
                    Task task = new Task(taskData, detailsData, dateData);
                    mViewModel.insert(task);
                } else {
                    Task task = new Task(taskData, detailsData, null);
                    mViewModel.insert(task);
                }
            }
        } else if (requestCode == UPDATE_TASK_REQUEST_CODE && resultCode == RESULT_OK) {
            if (data != null) {
                int id = data.getIntExtra(NewTaskActivity.EXTRA_REPLY_ID, -1);
                if (id == -1) {
                    Toast.makeText(this, R.string.cannot_be_updated, Toast.LENGTH_SHORT).show();
                    return;
                }
                // Retrieve the extended data from the NewTaskActivity intent.
                String taskData = data.getStringExtra(NewTaskActivity.EXTRA_REPLY_TASK);
                String detailsData = data.getStringExtra(NewTaskActivity.EXTRA_REPLY_DETAILS);
                long date = data.getLongExtra(NewTaskActivity.EXTRA_REPLY_DATE, 0);
                Date dateData = new Date(date);

                // Update the edited task.
                if (date != 0) {
                    Task task = new Task(taskData, detailsData, dateData);
                    task.setId(id);
                    mViewModel.update(task);
                } else {
                    Task task = new Task(taskData, detailsData, null);
                    task.setId(id);
                    mViewModel.update(task);
                }
            }
        } else {
            Snackbar.make(mCoordinatorLayout, "Task not saved.", Snackbar.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate a menu in this activity's toolbar
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    /**
     * Prepare the Screen's standard options menu to be displayed.
     * This is called right before the menu is shown, every time it is shown.
     */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // Retrieve the saved boolean in the shared prefs file.
        isItemVisible = mSharedPrefs.getBoolean(VISIBILITY_KEY, isItemVisible);

        // This will show and hide the particular icon of the menu.
        if (isItemVisible) {
            menu.findItem(R.id.linear_view).setVisible(true);
            menu.findItem(R.id.staggered_view).setVisible(false);
        } else {
            menu.findItem(R.id.linear_view).setVisible(false);
            menu.findItem(R.id.staggered_view).setVisible(true);
        }

        // Set the item layout
        if (menu.findItem(R.id.linear_view).isVisible()) {
            mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        } else {
            mRecyclerView.setLayoutManager(new StaggeredGridLayoutManager(2,
                    StaggeredGridLayoutManager.VERTICAL));
        }
        return super.onPrepareOptionsMenu(menu);
    }

    /**
     * This hook is called whenever an item in your options menu is selected.
     */
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        SharedPreferences.Editor editor = mSharedPrefs.edit();
        int id = item.getItemId();
        switch (id) {
            case R.id.delete_all_tasks:
                confirmDeleteAllTasks(); // Delete all tasks.
                return true;
            case R.id.log_out:
                logoutUser(); // Log out user.
                return true;
            case R.id.linear_view:
                item.setIcon(R.drawable.ic_round_dashboard_24);
                isItemVisible = false;
                saveItemVisibility(editor);
                return true;
            case R.id.staggered_view:
                item.setIcon(R.drawable.ic_round_view_agenda_24);
                isItemVisible = true;
                saveItemVisibility(editor);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Do not close the app immediately.
     */
    @Override
    public void onBackPressed() {
        if (beforeExitTime + 2000 > System.currentTimeMillis()) {
            mPressAgainSnackBar.dismiss();
            super.onBackPressed();
        } else {
            mPressAgainSnackBar = Snackbar.make(mCoordinatorLayout, "Tap again to exit.",
                    Snackbar.LENGTH_SHORT);
            mPressAgainSnackBar.show();
        }
        beforeExitTime = System.currentTimeMillis();
    }

    /**
     * Save the menu item visibility in the shared pref file.
     */
    private void saveItemVisibility(SharedPreferences.Editor editor) {
        editor.putBoolean(VISIBILITY_KEY, isItemVisible);
        editor.apply();
        this.invalidateOptionsMenu();
    }

    /**
     * Ask user to confirm delete all tasks.
     */
    private void confirmDeleteAllTasks() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        }).create();
        // If there are no tasks in the display, ask user if want to add a tasks.
        if (mAdapter.getItemCount() == 0) {
            builder.setTitle(R.string.no_task).setMessage(R.string.no_task_message)
                    .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            createNewTask();
                        }
                    })
                    .create()
                    .show();
        } else {
            builder.setTitle(R.string.delete_all_tasks).setMessage(R.string.delete_all_tasks_message)
                    .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            mViewModel.deleteAll();
                        }
                    })
                    .create()
                    .show();
        }
    }

    /**
     * Bring user to NewTaskActivity to create a task.
     */
    private void createNewTask() {
        Intent intent = new Intent(MainActivity.this, NewTaskActivity.class);
        startActivityForResult(intent, NEW_TASK_REQUEST_CODE);
    }

    /**
     * Bring user to an activity to edit the selected task.
     */
    private void launchEditActivity(Task task) {
        Intent intent = new Intent(MainActivity.this, NewTaskActivity.class);
        intent.putExtra(EXTRA_DATA_ID, task.getId());
        intent.putExtra(EXTRA_DATA_UPDATE_TASK, task.getTask());
        intent.putExtra(EXTRA_DATA_UPDATE_DETAILS, task.getDetails());
        if (task.getDate() != null) {
            long date = task.getDate().getTime();
            intent.putExtra(EXTRA_DATA_UPDATE_DATE, date);
        } // No date from the selected task.
        startActivityForResult(intent, UPDATE_TASK_REQUEST_CODE);
    }

    /**
     * Log out user account.
     */
    private void logoutUser() {
        mAuth.signOut();
        Intent intent = new Intent(MainActivity.this, LoginActivity.class);
        startActivity(intent);
        finish();
    }
}
package com.Pau.ImapNotes2;

import java.util.ArrayList;
import java.util.Date;

import com.Pau.ImapNotes2.R;
import com.Pau.ImapNotes2.Data.ConfigurationFile;
import com.Pau.ImapNotes2.Data.NotesDb;
import com.Pau.ImapNotes2.Miscs.Imaper;
import com.Pau.ImapNotes2.Miscs.OneNote;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.SimpleAdapter;
import android.widget.ListView;
import android.widget.EditText;
import android.text.Html;

public class Listactivity extends Activity {
	private static final int LOGIN_BUTTON = 0;
	private static final int REFRESH_BUTTON = 1;
	private static final int SEE_DETAIL = 2;
	private static final int DELETE_BUTTON = 3;
	private static final int NEW_BUTTON = 4;
	private static final int SAVE_BUTTON = 5;
	private static final int EDIT_BUTTON = 6;
		
	private ArrayList<OneNote> noteList;
	private SimpleAdapter listToView;
	
	private ConfigurationFile settings;
	private Imaper imapFolder;
	private NotesDb storedNotes;
	private OneNote currentNote;
	private static final String TAG = "IN_Listactivity";
	
	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
	super.onCreate(savedInstanceState);
	setContentView(R.layout.main);
	
	this.noteList = new ArrayList<OneNote>();
	((ImapNotes2)this.getApplicationContext()).SetNotesList(this.noteList);
	this.listToView = new SimpleAdapter(
			getApplicationContext(),
			this.noteList,
			R.layout.note_element,
			new String[]{"title","date"},
			new int[]{R.id.noteTitle, R.id.noteInformation});
	((ListView)findViewById(R.id.notesList)).setAdapter(this.listToView);
	
	this.settings = new ConfigurationFile(this.getApplicationContext());
	((ImapNotes2)this.getApplicationContext()).SetConfigurationFile(this.settings);
	
	this.imapFolder = new Imaper();
	((ImapNotes2)this.getApplicationContext()).SetImaper(this.imapFolder);
	
	this.storedNotes = new NotesDb(this.getApplicationContext());
	
	if (this.settings.GetUsername()==null && this.settings.GetPassword()==null && this.settings.GetServer()==null){
	    startActivityForResult(new Intent(this, AccontConfigurationActivity.class), Listactivity.LOGIN_BUTTON);
	
	} else {
		this.storedNotes.OpenDb();
		this.storedNotes.GetStoredNotes(this.noteList);
		this.listToView.notifyDataSetChanged();
		this.storedNotes.CloseDb();
	}
	
	// When item is clicked, we go to NoteDetailActivity
	((ListView)findViewById(R.id.notesList)).setOnItemClickListener(new OnItemClickListener() {
		public void onItemClick(AdapterView<?> arg0, View widget, int selectedNote, long arg3) {
			Intent toDetail = new Intent(widget.getContext(), NoteDetailActivity.class);
			toDetail.putExtra("selectedNote", (OneNote)noteList.get(selectedNote));
			startActivityForResult(toDetail,SEE_DETAIL); 
		}
	  });
    }

    public void RefreshList(){
		ProgressDialog loadingDialog = ProgressDialog.show(this, "ImapNotes2" , "Refreshing notes list... ", true);

		new RefreshThread().execute(this.imapFolder, this.settings, this.noteList, this.listToView, loadingDialog, this.storedNotes);

    }
    
    class RefreshThread extends AsyncTask<Object, Void, Boolean>{
    	SimpleAdapter adapter;
    	ArrayList<OneNote> notesList;
    	NotesDb storedNotes;
    	
		@Override
		protected Boolean doInBackground(Object... stuffs) {
			this.adapter = ((SimpleAdapter)stuffs[3]);
			this.notesList = ((ArrayList<OneNote>)stuffs[2]);
			this.storedNotes = ((NotesDb)stuffs[5]);
	
			try {
				if(!((Imaper)stuffs[0]).IsConnected())
					((Imaper)stuffs[0]).ConnectToProvider(
						((ConfigurationFile)stuffs[1]).GetUsername(),
						((ConfigurationFile)stuffs[1]).GetPassword(),
						((ConfigurationFile)stuffs[1]).GetServer());
				((Imaper)stuffs[0]).GetNotes(this.notesList);
		    	return true;
			} catch (Exception e) {
				Log.v("ImapNotes2", e.getMessage());
			} finally {
				((ProgressDialog)stuffs[4]).dismiss();
				
			}
			
			return false;
		}
		
		protected void onPostExecute(Boolean result){
			if(result){
				this.storedNotes.OpenDb();
				this.storedNotes.ClearDb();
				for(OneNote n : this.notesList)
					this.storedNotes.InsertANote(n);
				this.storedNotes.CloseDb();
						
				this.adapter.notifyDataSetChanged();
			}
		}
    	
    }
    
    public void NewMessage(){
	Intent editNew = new Intent(this, NewNoteActivity.class);
	startActivityForResult(editNew, NEW_BUTTON);
    }

    public int getIndexByNumber(String pNumber)
    {
        for(OneNote _item : this.noteList)
        {
            if(_item.GetNumber().equals(pNumber))
                return this.noteList.indexOf(_item);
        }
        return -1;
    }

    public void DeleteMessage(String numInImap){
//	Log.d(TAG,"Received request to delete message #"+numInImap);
	Integer numInImapInt = new Integer(numInImap);
	try {
		this.imapFolder.DeleteNote(numInImapInt);
                // Here we delete the note from the local notes list
                this.noteList.remove(getIndexByNumber(numInImap));
                this.listToView.notifyDataSetChanged();
	} catch (Exception ex) {
		Log.d(TAG,"Exception catched: " + ex.getMessage());
	}
    }

    public void AddMessage(String snote){
//        Log.d(TAG,"Received request to add new message");
        try {
                String[] tok = snote.split("(?i)<br>", 2);
                String title = Html.fromHtml(tok[0]).toString();
                String body = "<html><head></head><body>" + snote.substring(3, snote.length()-5) + "</body></html>";
                this.currentNote = new OneNote(title,body,new Date().toLocaleString(),"");
                // Here we ask to add the new note to the "Notes" folder
                ((ImapNotes2)this.getApplicationContext()).GetImaper().AddNote(this.currentNote);
                // Here we add the new note to the local notes list
                this.noteList.add(0,this.currentNote);
                this.listToView.notifyDataSetChanged();
        } catch (Exception ex) {
                Log.d(TAG,"Exception catched: " + ex.getMessage());
        }
    }

    /***************************************************/
    public boolean onCreateOptionsMenu(Menu menu){
	menu.add(0, Listactivity.LOGIN_BUTTON, 0, "Account");
	//.setIcon(R.drawable.ic_menu_barcode);
	menu.add(0, Listactivity.REFRESH_BUTTON, 0, "Refresh");
	menu.add(0, Listactivity.NEW_BUTTON, 0, "New");
	
	return true;

    }
    
    public boolean onOptionsItemSelected (MenuItem item){
	switch (item.getItemId()){
		case Listactivity.LOGIN_BUTTON:
		startActivityForResult(new Intent(this, AccontConfigurationActivity.class), Listactivity.LOGIN_BUTTON);
		return true;
		case Listactivity.REFRESH_BUTTON:
			if(this.settings.GetUsername()==null && this.settings.GetPassword()==null && this.settings.GetServer()==null)
				startActivityForResult(new Intent(this, AccontConfigurationActivity.class), Listactivity.LOGIN_BUTTON);
			else
				this.RefreshList();
			return true;
		case Listactivity.NEW_BUTTON:
			this.NewMessage();
			return true;
		    
	}
	
	return false;
	
    }
    
    /***************************************************/
    protected void onActivityResult(int requestCode, int resultCode, Intent data){ 
    	switch(requestCode) {
    		case Listactivity.LOGIN_BUTTON:
    			if(resultCode==AccontConfigurationActivity.TO_REFRESH)
    				this.RefreshList();
    		case Listactivity.SEE_DETAIL:
			// Returning from NoteDetailActivity
			if (resultCode == this.DELETE_BUTTON) {
				// Delete Message asked for
				// String numInImap will contain the Message Imap Number to delete
				String numInImap = data.getStringExtra("DELETE_ITEM_NUM_IMAP");
				DeleteMessage(numInImap);
			}
			if (resultCode == this.EDIT_BUTTON) {
				String txt = data.getStringExtra("EDIT_ITEM_TXT");
				String numInImap = data.getStringExtra("EDIT_ITEM_NUM_IMAP");
//				Log.d(TAG,"Received request to delete message:"+numInImap);
//				Log.d(TAG,"Received request to replace message with:"+txt);
				DeleteMessage(numInImap);
				this.AddMessage(txt);
			}
    		case Listactivity.NEW_BUTTON:
			// Returning from NewNoteActivity
			if (resultCode == this.SAVE_BUTTON) {
				String res = data.getStringExtra("SAVE_ITEM");
//				Log.d(TAG,"Received request to save message:"+res);
				this.AddMessage(res);
			}
    	}
    }
}
package Cache;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Calendar;
import java.util.Date;
import Database.DatabaseManager;


/* example:
 * 
 * 			PreparedStatement query = conn.prepareStatement("select * from people where salary > ?");
			query.setInt(1, 50000);
 */

public class CacheItem {
	
	static Connection conn = DatabaseManager.getConnection();
	static final String findNumberOfAuthors = "select count(id) from people where id in( select author_id from scmlog, actions where scmlog.id = actions.commit_id and date <=? and date >= ? and file_id = ?)"; // with ? symbols
	static final String findNumberOfChanges = "select count(actions.id) from actions, scmlog where actions.commit_id = scmlog.id and date <=? and date >=? and file_id=?";
	static final String findNumberOfBugs = "select count(commit_id) from actions where file_id=? and commit_id in (select id from scmlog where is_bug_fix=1 and date <=? and date >=?)";
	static final String findLoc = "select loc from content_loc where file_id=? and commit_id =?";
	private static PreparedStatement findNumberOfAuthorsQuery;
	private static PreparedStatement findNumberOfChangesQuery;
	private static PreparedStatement findNumberOfBugsQuery;
	private static PreparedStatement findLocQuery;

	public enum CacheReason{Prefetch, CoChange, NewEntity, ModifiedEntity, BugEntity}
	private final int entityId;
	private Date loadDate; // changed on cache hit
	private int LOC;
	private int numberOfChanges;
	private int numberOfBugs;
	private int numberOfAuthors;
	
	//XXX do we need these fields?
	private CacheReason loadType;
	private String commitDate; // for debugging?

	
	Statement stmt;
	StringBuilder sql = new StringBuilder();
	ResultSet r;

	public CacheItem(int eid, int cid, String cdate, CacheReason reas, String sdate)
	{

		entityId = eid;
		commitDate = cdate;
		update(reas, cid, cdate, sdate);
	}

	PreparedStatement getAuthorsStatement(){
		if (findNumberOfAuthorsQuery == null)
			try {
				findNumberOfAuthorsQuery = conn.prepareStatement(findNumberOfAuthors);
			} catch (SQLException e) {
				e.printStackTrace();
			}
		return findNumberOfAuthorsQuery;	
	}
	
	PreparedStatement getChangesStatement(){
		if (findNumberOfChangesQuery == null)
			try {
				findNumberOfChangesQuery = conn.prepareStatement(findNumberOfChanges);
			} catch (SQLException e) {
				e.printStackTrace();
			}
		return findNumberOfChangesQuery;	
	}
	
	PreparedStatement getBugsStatement(){
		if (findNumberOfBugsQuery == null)
			try {
				findNumberOfBugsQuery = conn.prepareStatement(findNumberOfBugs);
			} catch (SQLException e) {
				e.printStackTrace();
			}
		return findNumberOfBugsQuery;	
	}

	PreparedStatement getLocStatement(){
		if(findLocQuery == null)
		{
			try{
				findLocQuery = conn.prepareStatement(findLoc);
			}catch(SQLException e)
			{
				e.printStackTrace();
			}
		}
		return findLocQuery;
	}
	public void update(CacheReason reas, int cid, String cdate, String sdate){
		loadDate = Calendar.getInstance().getTime(); // XXX fix this, deprecated method
		LOC = findLoc(entityId, cid);
		numberOfChanges = findNumberOfChanges(entityId, cdate, sdate);
		numberOfBugs = findNumberOfBugs(entityId, cdate, sdate);
		numberOfAuthors = findNumberOfAuthors(entityId, cdate, sdate);
		loadType = reas;
	}


	/**
	 * @return Returns the entityId.
	 */
	public int getEntityId() {
		return entityId;
	}

	/**
	 * @return Returns the cachedDate.
	 */
	public Date getCachedDate() {
		return loadDate;
	}

	/**
	 * @return Returns the LOC.
	 */
	public int getLOC() {
		return LOC;
	}

	/**
	 * @return Returns the numberOfChanges.
	 */
	public int getNumberOfChanges() {
		return numberOfChanges;
	}


	/**
	 * @return Returns the numberOfBugs.
	 */
	public int getNumberOfBugs() {
		return numberOfBugs;
	}
	/**
	 * @return Returns the numberOfAuthors
	 */
	public int getNumberOfAuthors() {
		return numberOfAuthors;
	}

	private int findNumberOfAuthors(int eid, String cdate, String start) {
		int ret = 0;
		
		final PreparedStatement authorQuery = getAuthorsStatement();
		try {
			authorQuery.setString(1, cdate);
			authorQuery.setString(2, start);
			authorQuery.setInt(3, eid);
			ret = Util.Database.getIntResult(authorQuery);
		} catch (SQLException e1) {
			e1.printStackTrace();
		}
		return ret;
	}
	
	private int findNumberOfChanges(int eid, String cdate, String start) {
		int ret = 0;
		
		final PreparedStatement changeQuery = getChangesStatement();
		try {
			changeQuery.setString(1, cdate);
			changeQuery.setString(2, start);
			changeQuery.setInt(3, eid);
			ret = Util.Database.getIntResult(changeQuery);
		} catch (SQLException e1) {
			e1.printStackTrace();
		}
		return ret;
	}

	
	private int findNumberOfBugs(int eid, String cdate, String start) {
		
		int ret = 0;
		final PreparedStatement bugQuery = getBugsStatement();
		try {
			bugQuery.setInt(1, eid);
			bugQuery.setString(2, cdate);
			bugQuery.setString(3, start);
			ret = Util.Database.getIntResult(bugQuery);
		} catch (SQLException e1) {
			e1.printStackTrace();
		}
		return ret;
	}
	
	private int findLoc(int eid, int cid)
	{
		int ret = 0;
		final PreparedStatement locQuery = getLocStatement();
		try{
			locQuery.setInt(1, eid);
			locQuery.setInt(2, cid);
			ret = Util.Database.getIntResult(locQuery);
		}catch(SQLException e1){
			e1.printStackTrace();
		}
		return ret;
	}
	/*
		int numAuthor = 0;
		//		sql = "select count(distinct(author_id)) from scmlog where id in(" + //two slow to find the number of authors from the database
		//				"select commit_id from actions where file_id="+eid+" and commit_id between "+Simulator.STARTIDDEFAULT +" and "+cid +")";//???start_Id
		sql.setLength(0);
		sql.append("select count(id) from people where id in( select author_id from scmlog, actions where scmlog.id = actions.commit_id and date <='"+cdate+"' and date >= '"+start +"' and file_id = "+eid+")");
//		sql = "select count(id) from people where id in( select author_id from scmlog, actions where scmlog.id = actions.commit_id and date <='"+cdate+"' and date >= '"+start +"' and file_id = "+eid+")";
		try
		{
			stmt = conn.createStatement();

			r = stmt.executeQuery(sql.toString());
			if(r.next())
			{
				numAuthor = r.getInt(1);
			}
			else
				// error
			if(r.next())
				//error
			 

		}catch (Exception e) {
			System.out.println(e);
			System.exit(0);
		}		

		return numAuthor;
		*/
	
/*
	private int findNumberOfBugs(int eid, String cdate, String start) {
		int numBugs = 0;
		sql.setLength(0);
		sql.append("select count(commit_id) from actions where file_id="+eid+" and commit_id in (select id from scmlog where is_bug_fix=1 and date <= '"+cdate+"' and date >='"+start+"')");
//		sql = "select count(commit_id) from actions where file_id="+eid+" and commit_id in" +
//		"(select id from scmlog where is_bug_fix=1 and date <= '"+cdate+"' and date >='"+start+"')";
		try
		{
			stmt = conn.createStatement();
			r = stmt.executeQuery(sql.toString());
			while(r.next()){
				numBugs = r.getInt(1);
			}

		}catch (Exception e) {
			System.out.println(e);
			System.exit(0);
		}		
		return numBugs;
	}
/*
	private int findNumberOfChanges(int eid, String cdate, String start) {
		
		int numChanges = 0;
		sql.setLength(0);
		sql.append("select count(actions.id) from actions, scmlog where actions.commit_id = scmlog.id and date <='"+cdate+"' and date >='"+ start+"' and file_id="+eid);
//		sql = "select count(actions.id) from actions, scmlog where actions.commit_id = scmlog.id and date <='"+cdate+"' and date >='"+ start+"' and file_id="+eid;
		try
		{
			stmt = conn.createStatement();
			r = stmt.executeQuery(sql.toString());
			while(r.next())
			{
				numChanges = r.getInt(1);
			}

		}catch (Exception e) {
			System.out.println(e);
			System.exit(0);
		}		

		return numChanges;
	}
	// TODO: fix to use commit id to ensure unique lookup
	private int findLoc(int eid, int cid) {
		int loc =0;
		sql.setLength(0);
		sql.append("select loc from content_loc where file_id="+eid+" and commit_id = "+cid);
//		sql = "select loc from content_loc where file_id="+eid+" and commit_id = "+cid;
		try
		{
			stmt = conn.createStatement();
			r = stmt.executeQuery(sql.toString());
			while(r.next())
			{
				loc = r.getInt(1);
			}

		}catch (Exception e) {
			System.out.println(e);
			System.exit(0);
		}		
		return loc;
	}

	*/
}

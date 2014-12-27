package jp.gecko655.fujimiya.bot;

import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import twitter4j.Status;
import twitter4j.StatusUpdate;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.conf.ConfigurationBuilder;

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.customsearch.Customsearch;
import com.google.api.services.customsearch.model.Result;
import com.google.api.services.customsearch.model.Search;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;

public abstract class AbstractCron extends HttpServlet{

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    static Logger logger = Logger.getLogger("Fujimiya"); //$NON-NLS-1$
    
    static String consumerKey = Messages.getString("AbstractCron.consumerKey"); //$NON-NLS-1$
    static String consumerSecret = Messages.getString("AbstractCron.consumerSecret"); //$NON-NLS-1$
    static String accessToken = Messages.getString("AbstractCron.accessToken"); //$NON-NLS-1$
    static String accessTokenSecret = Messages.getString("AbstractCron.accessTokenSecret"); //$NON-NLS-1$

    static Twitter twitter;
    static Customsearch.Builder builder = new Customsearch.Builder(new NetHttpTransport(), new JacksonFactory(), null).setApplicationName("Google"); //$NON-NLS-1$
    static Customsearch search = builder.build();
    static DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
    
    public AbstractCron() {
        logger.setLevel(Level.FINE);
    }
    
    public void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        //http://twitter4j.org/ja/configuration.html
        ConfigurationBuilder cb = new ConfigurationBuilder();
        cb.setDebugEnabled(true)
            .setOAuthAccessToken(accessToken)
            .setOAuthAccessTokenSecret(accessTokenSecret)
            .setOAuthConsumerKey(consumerKey)
            .setOAuthConsumerSecret(consumerSecret);
        twitter = new TwitterFactory(cb.build()).getInstance();
        twitterCron();
    }

    /**
     * Search fujimiya-san's image and return the url.
     * The return is randomly picked up from the 100 result of google image search.
     * @param query
     * @return
     */
    FetchedImage getFujimiyaUrl(String query){
    	return getFujimiyaUrl(query,100);
    }
    /**
     * Search fujimiya-san's image and return the url.
     * The return is randomly picked up from the maxRankOfResult result of google image search.
     * @param query
     * @param maxRankOfResult
     * @return
     */
    FetchedImage getFujimiyaUrl(String query,int maxRankOfResult){
        try{
            //Get SearchResult
            Search search = getSearchResult(query, maxRankOfResult);
            List<Result> items = search.getItems();
            for(int i=0;i<10;i++){
                Result result = items.get(i);
                logger.log(Level.INFO,"query: " + query + " URL: "+result.getLink());
                logger.log(Level.INFO,"page URL: "+result.getImage().getContextLink());
                HttpURLConnection connection = (HttpURLConnection)(new URL(result.getLink())).openConnection();
                if(isInBlackList(result.getLink())){
                    continue;
                }
                connection.setRequestMethod("GET");
                connection.setInstanceFollowRedirects(false);
                connection.connect();
                if(connection.getResponseCode()==200){
                    return new FetchedImage(connection.getInputStream(),result.getLink());
                }else{
                    //retry.
                }
            }
            //If execution comes here, connection has failed 10 times.
            throw new ConnectException();

        } catch (MalformedURLException e) {
            // TODO Auto-generated catch block
            logger.log(Level.SEVERE,e.toString());
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            logger.log(Level.SEVERE,e.toString());
            e.printStackTrace();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            logger.log(Level.SEVERE,e.toString());
            e.printStackTrace();
        }
        return null;
}
    
    private boolean isInBlackList(String url) {
        Query q = new Query("ImageUrl").setFilter(new FilterPredicate(Entity.KEY_RESERVED_PROPERTY, FilterOperator.EQUAL, url));
        return ds.prepare(q).asIterator().hasNext();
    }

    private Search getSearchResult(String query, int maxRankOfResult) throws IOException {
            Customsearch.Cse.List list = search.cse().list(query); //$NON-NLS-1$
            
            list.setCx(Messages.getString("AbstractCron.cx")); //$NON-NLS-1$
            list.setKey(Messages.getString("AbstractCron.key")); //$NON-NLS-1$
            list.setSearchType("image"); //$NON-NLS-1$
            list.setNum(10L);
            list.setImgSize("huge").setImgSize("large").setImgSize("medium").setImgSize("xlarge").setImgSize("xxlarge");
            Search results = null;
            while(true){
                try{
                    long rand = (long)(Math.random()*maxRankOfResult+1);
                    list.setStart(rand);
                    logger.log(Level.INFO,"rand: "+rand);
                    results = list.execute();
                    if(results != null){
                        return results;
                    }
                }catch(IOException e){
                }
            }
        
    }

    protected void updateStatusWithMedia(StatusUpdate update, String query, int maxRankOfResult){
                    Status succeededStatus = null;
                    while(succeededStatus==null){
                        try{
                            FetchedImage fetchedImage = getFujimiyaUrl(query,maxRankOfResult);
                            update.media("fujimiya.jpg",fetchedImage.getInputStream());
                            succeededStatus = twitter.updateStatus(update);
                            Entity imageUrlEntity = new Entity("ImageUrl",succeededStatus.getId());
                            imageUrlEntity.setProperty("URL",fetchedImage.getUrl());
                            ds.put(imageUrlEntity);
                            logger.log(Level.INFO,"Successfully tweeted: "+succeededStatus.getText());
                        }catch(TwitterException e){
                            logger.log(Level.INFO,"updateStatusWithMedia failed. try again. "+ e.getErrorMessage());
                        }
                    }
        
    }
    
    abstract protected void twitterCron();


}

class FetchedImage{
    private InputStream in;
    private String url;
    public FetchedImage(InputStream in, String url) {
        this.in = in;
        this.url = url;
    }
    public InputStream getInputStream() {
        return in;
    }
    public String getUrl() {
        return url;
    }
}
package jp.gecko655.fujimiya.bot;

import twitter4j.StatusUpdate;

public class FujimiyaLunch extends AbstractCron{

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    @Override
    protected void twitterCron() {
        //Twitterに書き出し
        StatusUpdate status =new StatusUpdate(" "); //$NON-NLS-1$
        updateStatusWithMedia(status, "藤宮さん 昼", 10);//$NON-NLS-1$
        
    }

}

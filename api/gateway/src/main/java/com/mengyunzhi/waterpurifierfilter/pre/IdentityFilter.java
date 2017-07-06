package com.mengyunzhi.waterpurifierfilter.pre;

import com.google.common.collect.Maps;
import com.mengyunzhi.waterpurifierfilter.service.IdentityFilterService;
import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import net.sf.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.SessionAttributes;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Created by chuhang on 2017/6/19.
 * 身份验证过滤器（用于判断请求是不是我们的用户发出的）
 */
@SessionAttributes("user")
public class IdentityFilter extends ZuulFilter {

    private static Logger logger = LoggerFactory.getLogger(IdentityFilter.class);

    @Autowired
    private IdentityFilterService identityFilterService;
    @Override
    public String filterType() {
        return "pre";
    }

    @Override
    public int filterOrder() {
        return 1;
    }

    @Override
    public boolean shouldFilter() {
        return true;
    }

    @Override
    public Object run()  {
        // 获取请求内容
        RequestContext ctx = RequestContext.getCurrentContext();
        //获取3rd_session
        String threeRdSession = ctx.getRequest().getParameter("threeRdSession");
        //如果threeRdSession不为空，则认为是微信端发送送的请求
        if (threeRdSession != null) {
            //获取openId
            HttpSession session = ctx.getRequest().getSession();
            Object openIdAndSessionKey = session.getAttribute(threeRdSession);
            String openid = JSONObject.fromObject(openIdAndSessionKey).getString("openid");
            //增加请求参数，根据客户端请求的参数3rd_session，从session中获取客户的openId
            this.addParams(openid, ctx);
        }
        //验证请求，若不合法，则拦截，反之
        this.verifyRequest(ctx);
        return null;
    }

    /**
     * 增加请求参数，根据客户端请求的参数3rd_session，从session中获取客户的openId
     * @param openid
     * @param ctx
     */
    public void addParams(String openid, RequestContext ctx) {
        Map<String, List<String>> params = ctx.getRequestQueryParams();
        //若无请求参数，则new一个map
        if (params == null) {
            params = Maps.newHashMap();
        }
        //添加参数
        List<String> param = new ArrayList<String>();
        param.add(0, openid);
        params.put("openid", param);
        ctx.setRequestQueryParams(params);
    }

    /**
     * 验证请求，若不合法，则拦截，反之
     * @param ctx
     */
    public void verifyRequest(RequestContext ctx) {
        //获取请求
        HttpServletRequest request = ctx.getRequest();
        //定义并赋空值
        String timestamp, randomString, encryptionInfo;
        timestamp = randomString = encryptionInfo = "";
        //判断请求方法，并获取相关加密信息
        if (request.getMethod().equals("GET")) {
            timestamp = request.getParameter("timestamp");
            randomString = request.getParameter("randomString");
            encryptionInfo = request.getParameter("encryptionInfo");
        } else {
            try {
                //获取请求string类型body（暂时无法将string反序列化json格式，暂时采取截取字符串的方法）
                StringBuilder sb = new StringBuilder();
                BufferedReader reader = request.getReader();
                try {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                } finally {
                    reader.close();
                }

                //正则表达式，匹配英文字母或者数字
                String s = "[^A-Za-z0-9]";
                Pattern pattern = Pattern.compile(s);

                //获取加密信息
                encryptionInfo = pattern.matcher(sb.substring(sb.lastIndexOf(":"), sb.lastIndexOf("}"))).replaceAll("").trim();
                //获取随机字符串
                String temp = sb.substring(0, sb.lastIndexOf(":")-1);
                randomString = pattern.matcher(temp.substring(temp.lastIndexOf(":"), temp.lastIndexOf(","))).replaceAll("").trim();
                //获取时间戳
                String temp2 = sb.substring(0,sb.lastIndexOf(",")-1);
                timestamp = temp2.substring(temp2.indexOf("timestamp")+12, temp2.lastIndexOf(","));
            } catch (IOException e) {
                System.out.println("IOException:" + e);
            }
        }
        // 验证信息是否为我们的客户发送的，若不是(除获取当前时间戳外)，拦截
        if (!identityFilterService.isTrue(timestamp, randomString, encryptionInfo) && request.getRequestURL().indexOf("/api/getCurrentTime") < 0) {
            ctx.setSendZuulResponse(false);
        }
        return;
    }
}

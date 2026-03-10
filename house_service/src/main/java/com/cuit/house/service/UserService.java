package com.cuit.house.service;

import com.cuit.house.mapper.UserMapper;
import com.cuit.house.pojo.User;
import com.cuit.house.utils.BeanHelper;
import com.cuit.house.utils.HashUtils;
import org.apache.commons.lang3.StringUtils;
import org.assertj.core.util.Lists;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

    @Value("${file.imgprefix}")
    private String imgPrefix;

    @Autowired
    private FileService fileService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private MailService mailService;

    public List<User> getUsers(){
        return userMapper.selectUsers();
    }
    /*
    1.密码加密
    2.图片存储
    3.邮件发送
     */
    public boolean addAcount(User account) {
        account.setPasswd(HashUtils.encryPassword(account.getPasswd()));
        List<String> imgList = fileService.getImgPath(Lists.newArrayList(account.getAvatarFile()));
        if(!imgList.isEmpty()){
            //存入数据库中
            account.setAvatar(imgList.get(0));
        }
        //把数据存入数据库中
        //设置初始值
        BeanHelper.setDefaultProp(account,User.class);
        BeanHelper.onInsert(account);
        //用户设置非激活
        account.setEnable(0);
        userMapper.insert(account);
        //邮件发送
        mailService.registerNotify(account.getEmail());
        return true;
    }

    public boolean enable(String key) {
        String email = mailService.registerCache.getIfPresent(key);
        if(StringUtils.isBlank(email)){
            return false;
        }
        User updateUser=new User();
        updateUser.setEmail(email);
        updateUser.setEnable(1);
        userMapper.update(updateUser);
        //清除缓存的邮件绑定的key
        mailService.registerCache.invalidate(key);
        return true;
    }

    public User auth(String username, String password) {
        User user=new User();
        user.setEmail(username);
        user.setPasswd(HashUtils.encryPassword(password));
        user.setEnable(1);
        List<User> list=getUserByQuery(user);
        if(!list.isEmpty()){
            return list.get(0);
        }
        return null;
    }

    private List<User> getUserByQuery(User user) {
        List<User> list=userMapper.selectUsersByQuerys(user);
        list.forEach(u->{
            u.setAvatar(imgPrefix+u.getAvatar());
        });
        return list;
    }
}

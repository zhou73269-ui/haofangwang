package com.cuit.house.service;

import com.cuit.house.enums.HouseUserType;
import com.cuit.house.mapper.HouseMapper;
import com.cuit.house.page.PageData;
import com.cuit.house.page.PageParams;
import com.cuit.house.pojo.*;
import com.cuit.house.utils.BeanHelper;
import com.google.common.base.Joiner;
import org.apache.commons.collections.CollectionUtils;
import org.assertj.core.util.Lists;
import org.assertj.core.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class HouseService {

    @Autowired
    private HouseMapper houseMapper;

    @Autowired
    private AgencyService agencyService;

    @Autowired
    private MailService mailService;
    
    @Autowired
    private FileService fileService;

    @Value("${file.imgprefix}")
    private String imgPrefix;

    //我们这个queryHouse要先通过小区名字，定位到小区id，通过小区id查询到房产，所以要先定义小区bean
    /*
     查询小区
     添加图片服务器地址前缀
     构建分页结果
     */
    public PageData<House> queryHouse(House query, PageParams pageParams) {
        List<House> houses = Lists.newArrayList();
    // 如果我们的条件部位null，就要去查询小区
        if(!Strings.isNullOrEmpty(query.getName())){
            Community community = new Community();
            community.setName(query.getName());
            List<Community> communities=houseMapper.selectCommunity(community);
            if(!communities.isEmpty()){
                query.setCommunityId(communities.get(0).getId());
            }
        }
        //查询所有的分页数据
        houses=queryAndSetImg(query,pageParams);
        //查询总数
        Long count = houseMapper.selectPageCount(query);
        return PageData.buildPage(houses,count,pageParams.getPageSize(),pageParams.getPageNum());
    }

    public List<House> queryAndSetImg(House query, PageParams pageParams) {
        List<House> houses = houseMapper.selectPageHouse(query, pageParams);
        houses.forEach(h->{
            h.setFirstImg(imgPrefix+h.getImages().split(",")[0]);
            h.setImageList(h.getImageList().stream().map
                    (img->imgPrefix+img).collect(Collectors.toList()));
            h.setFloorPlanList(h.getFloorPlanList().stream().map
                    (img->imgPrefix+img).collect(Collectors.toList()));
        });
        return houses;
    }

    public House queryOneHouese(Long id) {
        House query=new House();
        query.setId(id);
        List<House> houses = queryAndSetImg(query, PageParams.build(1, 1));
        if(!houses.isEmpty()){
            return houses.get(0);
        }
        return null;
    }

    public HouseUser getHouseUser(Long houseId) {
        HouseUser houseUser=houseMapper.selectSaleHouseUser(houseId);
        return houseUser;
    }

    public void addUserMsg(UserMsg userMsg) {
        BeanHelper.onInsert(userMsg);
        houseMapper.insertUserMsg(userMsg);
        User agent = agencyService.getAgentDetail(userMsg.getAgentId());
        mailService.sendMail("来自用户"+userMsg.getEmail()+"的留言",
                userMsg.getMsg(),agent.getEmail());
    }

    public List<House> getLasttest() {
        House query=new House();
        query.setSort("create_time");
        List<House> houses = queryAndSetImg(query, new PageParams(8, 1));
        return houses;
    }

    public List<Community> getAllCommunitys() {
        return houseMapper.selectCommunity(new Community());
    }
    /*
        添加房屋图片
        添加户型图片
        插入房产信息
        绑定用户到房产的关系(插入第三张表)
     */
    public void addHouse(House house, User user) {
        //判断用户有没有上传房屋的图片
        if(CollectionUtils.isNotEmpty(house.getHouseFiles())){
            //处理图片逗号分割
            String images = Joiner.on(",").
                    join(fileService.getImgPath(house.getHouseFiles()));
            house.setImages(images);
        }
        if(CollectionUtils.isNotEmpty(house.getFloorPlanList())){
            String images = Joiner.on(",").join(house.getFloorPlanList());
            house.setFloorPlan(images);
        }
        BeanHelper.onInsert(house);
        houseMapper.insert(house);
        //绑定用户到房产的关系，第三个参数是否收藏（插入第三章表）
        bindUserToHouse(house.getId(),user.getId(),false);
    }

    public void bindUserToHouse(Long houseId, Long userId, boolean collect) {
        HouseUser houseUser=new HouseUser();
        houseUser.setHouseId(houseId);
        houseUser.setUserId(userId);
        houseUser.setType(collect? HouseUserType.BOOKMARK.value : HouseUserType.SALE.value);
        BeanHelper.onInsert(houseUser);
        BeanHelper.setDefaultProp(houseUser,HouseUser.class);
        houseMapper.inserHouseUser(houseUser);
    }

    public void updateRating(Long id, Double rating) {
        House house = queryOneHouese(id);
        Double oldRating = house.getRating();
        //取平均值
        Double newRating =
                oldRating.equals(0D)?rating:Math.max((oldRating+rating)/2,5);
        House udapteHouse=new House();
        udapteHouse.setId(id);
        udapteHouse.setRating(newRating);
        BeanHelper.onInsert(udapteHouse);
        houseMapper.updateHouse(udapteHouse);
    }

    public void unbindUserToHouse(Long id, Long userId, HouseUserType type) {
        houseMapper.deleteHouseUser(id,userId,type.value);
    }
}

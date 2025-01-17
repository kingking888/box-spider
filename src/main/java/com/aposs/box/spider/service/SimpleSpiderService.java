package com.aposs.box.spider.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.aposs.box.spider.constant.enums.SpiderTypeEnum;
import com.aposs.box.spider.converter.SpiderMateDataConverter;
import com.aposs.box.spider.dao.SpiderMateDataDao;
import com.aposs.box.spider.dao.mongo.CommonDao;
import com.aposs.box.spider.model.dto.SpiderMateDataDto;
import com.aposs.box.spider.model.entity.PipeLineProperty;
import com.aposs.box.spider.model.entity.ProcessProperty;
import com.aposs.box.spider.model.entity.SpiderMateData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import us.codecraft.webmagic.Page;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.Spider;
import us.codecraft.webmagic.processor.PageProcessor;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 通用爬虫
 *
 * @Date 2021/6/20
 * @Created by Aaron
 */
@Service
@Slf4j
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class SimpleSpiderService {
    private final Site site = Site.me().setRetryTimes(3).setSleepTime(1000).setTimeOut(10000);

    private final CommonDao commonDao;
    private final SpiderMateDataDao spiderMateDataDao;

    /**
     * 执行所有简历类型爬虫任务
     */
    @Async
    public void runAllSimpleSpider() {
        List<SpiderMateData> spiderMateDataList = spiderMateDataDao.querySpiderMateData(SpiderTypeEnum.SIMPLE);
        List<SpiderMateDataDto> spiderMateDataDtoList = spiderMateDataList.stream().map(SpiderMateDataConverter::convertDto).collect(Collectors.toList());
        spiderMateDataDtoList.forEach(this::executeSimpleSpider);
    }


    /**
     * 根据爬虫元数据执行简单类型爬取任务
     *
     * @param spiderMateData 爬虫元数据
     */
    private void executeSimpleSpider(SpiderMateDataDto spiderMateData) {
        String spiderName = spiderMateData.getSpiderName();
        String url = spiderMateData.getSpiderUrl();
        ProcessProperty.Position position = spiderMateData.getProcessProperty().getPosition();
        PipeLineProperty pipeLineProperty = spiderMateData.getPipeLineProperty();
        String idColumn = pipeLineProperty.getIdColumn();

        Spider.create(new PageProcessor() {
            @Override
            public void process(Page page) {
                // 处理页面逻辑
                String pageString = page.getRawText();
                Object pageData = JSONObject.parse(pageString);
                JSON data = getData(pageData, position);
                page.putField(spiderName, data);
            }

            @Override
            public Site getSite() {
                return site;
            }
        }).addUrl(url).addPipeline((resultItems, task) -> {
            if (resultItems.isSkip()) {
                return;
            }
            JSONArray dataArray = resultItems.get(spiderName);
            if (dataArray != null && !dataArray.isEmpty()) {
                int size = dataArray.size();
                for (int i = 0; i < size; i++) {
                    JSONObject dataJsonObject = dataArray.getJSONObject(i);
                    commonDao.saveObject(dataJsonObject, spiderName, idColumn);
                }
                log.info("save data to MongoDB success! spiderName:{}, size:{}", spiderName, size);
            }
        }).run();
    }

    public JSON getData(Object parent, ProcessProperty.Position position) {
        String key = position.getKey();
        String type = position.getType();
        ProcessProperty.Position next = position.getNext();
        JSON child = null;
        if (parent instanceof JSONObject) {
            if (type.equals("object")) child = ((JSONObject) parent).getJSONObject(key);
            else if (type.equals("array")) child = ((JSONObject) parent).getJSONArray(key);
        } else if (parent instanceof JSONArray) {
            if (type.equals("object")) child = ((JSONArray) parent).getJSONObject(Integer.parseInt(key));
            else if (type.equals("array")) child = ((JSONArray) parent).getJSONArray(Integer.parseInt(key));
        }
        if (next != null) {
            return getData(child, next);
        }
        return child;

    }
}

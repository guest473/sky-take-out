package com.sky.service.impl;

import com.sky.constant.MessageConstant;
import com.sky.dto.GoodsSalesDTO;
import com.sky.entity.Orders;
import com.sky.exception.BaseException;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.ReportService;
import com.sky.vo.OrderReportVO;
import com.sky.vo.SalesTop10ReportVO;
import com.sky.vo.TurnoverReportVO;
import com.sky.vo.UserReportVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ReportServiceImpl implements ReportService {
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private UserMapper userMapper;

    @Override
    public TurnoverReportVO turnoverReport(LocalDate begin, LocalDate end) {
        validateRange(begin, end);
        List<LocalDate> dateList = buildDateList(begin, end);

        //整段区间一次聚合，再按日期装配，避免逐日查库
        Map<LocalDate, Double> turnoverMap = toAmountMap(
                orderMapper.sumByDay(beginTime(begin), endTime(end), Orders.COMPLETED));
        List<Double> turnoverList = new ArrayList<>();
        for (LocalDate date : dateList) {
            turnoverList.add(turnoverMap.getOrDefault(date, 0.0));
        }
        return TurnoverReportVO
                .builder()
                .dateList(StringUtils.join(dateList, ","))
                .turnoverList(StringUtils.join(turnoverList, ","))
                .build();
    }

    @Override
    public UserReportVO userReport(LocalDate begin, LocalDate end) {
        validateRange(begin, end);
        List<LocalDate> dateList = buildDateList(begin, end);

        //区间开始前已注册的用户数，作为累计用户数的基数
        Map<String, Object> beforeMap = new HashMap<>();
        beforeMap.put("end", beginTime(begin));
        Integer before = userMapper.countByMap(beforeMap);
        int totalUser = before == null ? 0 : before;

        //整段区间一次聚合出每日新增，再按日累加得到每日累计用户数
        Map<LocalDate, Long> newUserMap = toCountMap(
                userMapper.countNewUserByDay(beginTime(begin), endTime(end)));
        List<Integer> newUserList = new ArrayList<>();
        List<Integer> totalUserList = new ArrayList<>();
        for (LocalDate date : dateList) {
            int newUser = newUserMap.getOrDefault(date, 0L).intValue();
            totalUser += newUser;
            newUserList.add(newUser);
            totalUserList.add(totalUser);
        }
        return UserReportVO
                .builder()
                .dateList(StringUtils.join(dateList, ",")).
                totalUserList(StringUtils.join(totalUserList, ","))
                .newUserList(StringUtils.join(newUserList, ","))
                .build();
    }

    @Override
    public OrderReportVO ordersReport(LocalDate begin, LocalDate end) {
        validateRange(begin, end);
        List<LocalDate> dateList = buildDateList(begin, end);

        //整段区间一次聚合出每日订单总数与有效订单数
        Map<LocalDate, Long> totalMap = toCountMap(
                orderMapper.countByDay(beginTime(begin), endTime(end), null));
        Map<LocalDate, Long> validMap = toCountMap(
                orderMapper.countByDay(beginTime(begin), endTime(end), Orders.COMPLETED));

        List<Integer> validOrderCountList = new ArrayList<>();
        List<Integer> totalOrderCountList = new ArrayList<>();
        for (LocalDate date : dateList) {
            validOrderCountList.add(validMap.getOrDefault(date, 0L).intValue());
            totalOrderCountList.add(totalMap.getOrDefault(date, 0L).intValue());
        }
        //汇总数与图表口径保持一致：取所选区间内逐日统计的合计
        int totalOrderCount = totalOrderCountList.stream().mapToInt(Integer::intValue).sum();
        int validOrderCount = validOrderCountList.stream().mapToInt(Integer::intValue).sum();
        Double orderCompletionRate = 0.0;
        if (totalOrderCount != 0) {
            orderCompletionRate = (double) validOrderCount / totalOrderCount;
        }
        return OrderReportVO
                .builder()
                .dateList(StringUtils.join(dateList, ","))
                .orderCountList(StringUtils.join(totalOrderCountList, ","))
                .validOrderCountList(StringUtils.join(validOrderCountList, ","))
                .totalOrderCount(totalOrderCount)
                .validOrderCount(validOrderCount)
                .orderCompletionRate(orderCompletionRate)
                .build();
    }

    @Override
    public SalesTop10ReportVO getSalesTop10(LocalDate begin, LocalDate end) {
        validateRange(begin, end);
        LocalDateTime beginTime = LocalDateTime.of(begin, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(end, LocalTime.MAX);

        List<GoodsSalesDTO> salesTop10 = orderMapper.getSalesTop10(beginTime, endTime);
        List<String> names = salesTop10.stream().map(GoodsSalesDTO::getName).collect(Collectors.toList());
        String nameList = StringUtils.join(names, ",");

        List<Integer> numbers = salesTop10.stream().map(GoodsSalesDTO::getNumber).collect(Collectors.toList());
        String numberList = StringUtils.join(numbers, ",");

        //封装返回结果数据
        return SalesTop10ReportVO
                .builder()
                .nameList(nameList)
                .numberList(numberList)
                .build();
    }

    //按天枚举闭区间 [begin, end]
    private List<LocalDate> buildDateList(LocalDate begin, LocalDate end) {
        List<LocalDate> dateList = new ArrayList<>();
        for (LocalDate date = begin; !date.isAfter(end); date = date.plusDays(1)) {
            dateList.add(date);
        }
        return dateList;
    }

    private LocalDateTime beginTime(LocalDate date) {
        return LocalDateTime.of(date, LocalTime.MIN);
    }

    private LocalDateTime endTime(LocalDate date) {
        return LocalDateTime.of(date, LocalTime.MAX);
    }

    //把按日聚合的计数结果转成 日期 -> 数量
    private Map<LocalDate, Long> toCountMap(List<Map<String, Object>> rows) {
        Map<LocalDate, Long> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            result.put(LocalDate.parse(String.valueOf(row.get("stat_date"))),
                    ((Number) row.get("stat_value")).longValue());
        }
        return result;
    }

    //把按日聚合的金额结果转成 日期 -> 金额
    private Map<LocalDate, Double> toAmountMap(List<Map<String, Object>> rows) {
        Map<LocalDate, Double> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            result.put(LocalDate.parse(String.valueOf(row.get("stat_date"))),
                    ((Number) row.get("stat_value")).doubleValue());
        }
        return result;
    }

    //校验报表日期区间：缺失时避免空指针，颠倒时避免静默返回错误结果
    private void validateRange(LocalDate begin, LocalDate end) {
        if (begin == null || end == null) {
            throw new BaseException(MessageConstant.REPORT_DATE_IS_NULL);
        }
        if (begin.isAfter(end)) {
            throw new BaseException(MessageConstant.REPORT_DATE_ILLEGAL);
        }
    }

}

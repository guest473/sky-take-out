package com.sky.controller.admin;

import com.sky.result.Result;
import com.sky.service.ReportService;
import com.sky.vo.OrderReportVO;
import com.sky.vo.SalesTop10ReportVO;
import com.sky.vo.TurnoverReportVO;
import com.sky.vo.UserReportVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/admin/report")
@Slf4j
public class ReportController {
    @Autowired
    private ReportService reportService;

    @GetMapping("/turnoverStatistics")
    public Result<TurnoverReportVO> turnoverReport(@DateTimeFormat (pattern = "yyyy-MM-dd") LocalDate begin,
                                                   @DateTimeFormat (pattern = "yyyy-MM-dd")LocalDate end){
        return Result.success(reportService.turnoverReport(begin,end));
    }

    @GetMapping("/userStatistics")
    public Result<UserReportVO> userReport(@DateTimeFormat (pattern = "yyyy-MM-dd") LocalDate begin,
                                           @DateTimeFormat (pattern = "yyyy-MM-dd")LocalDate end){
        return Result.success(reportService.userReport(begin,end));
    }

    @GetMapping("/ordersStatistics")
    public Result<OrderReportVO> ordersReport(@DateTimeFormat (pattern = "yyyy-MM-dd") LocalDate begin,
                                              @DateTimeFormat (pattern = "yyyy-MM-dd")LocalDate end){
        return Result.success(reportService.ordersReport(begin,end));
    }

    @GetMapping("/top10")
    public Result<SalesTop10ReportVO> getTop10(@DateTimeFormat (pattern = "yyyy-MM-dd") LocalDate begin,
                                               @DateTimeFormat (pattern = "yyyy-MM-dd")LocalDate end){
        return Result.success(reportService.getSalesTop10(begin, end));
    }

}

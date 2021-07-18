package com.quartz.quartzeg.controller;

import com.quartz.quartzeg.Job.MessageJob;
import com.quartz.quartzeg.config.QuartzConfiguration;
import com.quartz.quartzeg.dto.MessageDTO;
import com.quartz.quartzeg.entity.Message;
import com.quartz.quartzeg.repository.MessageRepository;

import org.quartz.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Date;
import java.util.Optional;

@RestController
@RequestMapping(path="/messages")
public class MessageController {
    @Autowired
    private QuartzConfiguration quartzConfig;

    @Autowired
    private MessageRepository messageRepository;

    @PostMapping(path = "/schedule-visibility")
    public MessageDTO scheduleMessageVisibility(@RequestBody MessageDTO messageDto) {
        try {
            Message message = new Message();
            message.setContent(messageDto.getContent());
            message.setVisible(false);
            message.setMakeVisibleAt(messageDto.getMakeVisibleAt());

            message = messageRepository.save(message);

            //creating job detail instance
            String id = String.valueOf(message.getId());
            JobDetail jobDetail = JobBuilder.newJob(MessageJob.class).withIdentity(id).build();

            // adding jobdatamap to jobdetail
            jobDetail.getJobDataMap().put("messageId", id);

            //scheduling time to run job
            Date triggerJobAt = new Date(message.getMakeVisibleAt());

            SimpleTrigger trigger = TriggerBuilder.newTrigger().withIdentity(id)
                    .startAt(triggerJobAt).withSchedule(SimpleScheduleBuilder.simpleSchedule()
                            .withMisfireHandlingInstructionFireNow())
                    .build();
            Scheduler scheduler = quartzConfig.schedulerFactoryBean().getScheduler();
            scheduler.scheduleJob(jobDetail, trigger);
            scheduler.start();

            messageDto.setStatus("SUCCESS");
        } catch (IOException | SchedulerException e)
        {
            // scheduling failed
            messageDto.setStatus("FAILED");
            e.printStackTrace();
        }
        return messageDto;

    }

    @DeleteMapping(path = "/{messageId}/unschedule-visibility")
    public @ResponseBody  MessageDTO unscheduleMessageVisibility(
            @PathVariable(name = "messageId") Integer messageId) {

        MessageDTO messageDto = new MessageDTO();

        Optional<Message> messageOpt = messageRepository.findById(messageId);
        if (!messageOpt.isPresent()) {
            messageDto.setStatus("Message Not Found");
            return messageDto;
        }

        Message message = messageOpt.get();
        message.setVisible(false);
        messageRepository.save(message);

        String id = String.valueOf(message.getId());

        try {
            Scheduler scheduler = quartzConfig.schedulerFactoryBean().getScheduler();

            scheduler.deleteJob(new JobKey(id));
            TriggerKey triggerKey = new TriggerKey(id);
            scheduler.unscheduleJob(triggerKey);
            messageDto.setStatus("SUCCESS");

        } catch (IOException | SchedulerException e) {
            messageDto.setStatus("FAILED");
            e.printStackTrace();
        }
        return messageDto;
    }

}

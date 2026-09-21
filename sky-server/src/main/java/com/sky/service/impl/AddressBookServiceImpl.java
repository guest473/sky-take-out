package com.sky.service.impl;

import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.entity.AddressBook;
import com.sky.exception.AddressBookBusinessException;
import com.sky.mapper.AddressBookMapper;
import com.sky.service.AddressBookService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
public class AddressBookServiceImpl implements AddressBookService {
    @Autowired
    private AddressBookMapper addressBookMapper;

    //条件查询
    public List<AddressBook> list(AddressBook addressBook) {
        return addressBookMapper.list(addressBook);
    }

    //新增地址
    public void save(AddressBook addressBook) {
        //手机号在库中为必填字段，先校验避免写库时报SQL异常
        if (!StringUtils.hasText(addressBook.getPhone())) {
            throw new AddressBookBusinessException(MessageConstant.PHONE_IS_NULL);
        }
        addressBook.setUserId(BaseContext.getCurrentId());
        addressBook.setIsDefault(0);
        addressBookMapper.insert(addressBook);
    }

    //根据id查询
    public AddressBook getById(Long id) {
        return getOwnAddressBook(id);
    }

    //根据id修改地址
    public void update(AddressBook addressBook) {
        //校验地址归属，避免修改他人地址
        getOwnAddressBook(addressBook.getId());
        addressBookMapper.update(addressBook);
    }

    //置默认地址
    @Transactional
    public void setDefault(AddressBook addressBook) {
        //校验地址归属，避免把他人地址设为默认
        getOwnAddressBook(addressBook.getId());

        //1、将当前用户的所有地址修改为非默认地址 update address_book set is_default = ? where user_id = ?
        addressBook.setIsDefault(0);
        addressBook.setUserId(BaseContext.getCurrentId());
        addressBookMapper.updateIsDefaultByUserId(addressBook);

        //2、将当前地址改为默认地址 update address_book set is_default = ? where id = ?
        addressBook.setIsDefault(1);
        addressBookMapper.update(addressBook);
    }

    //根据id删除地址
    public void deleteById(Long id) {
        //校验地址归属，避免删除他人地址
        getOwnAddressBook(id);
        addressBookMapper.deleteById(id);
    }

    //查询地址并校验其属于当前登录用户，避免横向越权
    private AddressBook getOwnAddressBook(Long id) {
        AddressBook addressBook = addressBookMapper.getById(id);
        if (addressBook == null || !Objects.equals(addressBook.getUserId(), BaseContext.getCurrentId())) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_NOT_FOUND);
        }
        return addressBook;
    }

}

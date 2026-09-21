package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.RoleConstant;
import com.sky.constant.StatusConstant;
import com.sky.context.BaseContext;
import com.sky.dto.EmployeeDTO;
import com.sky.dto.EmployeeLoginDTO;
import com.sky.dto.EmployeePageQueryDTO;
import com.sky.dto.PasswordEditDTO;
import com.sky.entity.Employee;
import com.sky.exception.AccountLockedException;
import com.sky.exception.AccountNotFoundException;
import com.sky.exception.BaseException;
import com.sky.exception.LoginFailedException;
import com.sky.exception.PasswordErrorException;
import com.sky.mapper.EmployeeMapper;
import com.sky.result.PageResult;
import com.sky.service.EmployeeService;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class EmployeeServiceImpl implements EmployeeService {

    //密码散列算法与参数：Argon2id（OWASP 推荐基线参数），密文格式 argon2id$m=..,t=..,p=..$盐$散列
    private static final String ARGON2_PREFIX = "argon2id$";
    private static final int ARGON2_SALT_BYTES = 16;
    private static final int ARGON2_HASH_BYTES = 32;
    private static final int ARGON2_ITERATIONS = 2;
    private static final int ARGON2_MEMORY_KB = 19456;
    private static final int ARGON2_PARALLELISM = 1;

    //历史密文仍可校验：PBKDF2（pbkdf2$迭代次数$盐$散列）与最初的无盐MD5
    private static final String PBKDF2_PREFIX = "pbkdf2$";
    private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int PBKDF2_HASH_BITS = 256;

    //Argon2id密文中的参数段，形如 m=19456,t=2,p=1
    private static final Pattern ARGON2_PARAM_PATTERN = Pattern.compile("m=(\\d+),t=(\\d+),p=(\\d+)");

    //登录失败次数限制：按"用户名+来源IP"计数，避免攻击者拿任意已知用户名把别人的账号锁死
    private static final String LOGIN_FAIL_KEY_PREFIX = "login:fail:";
    private static final int MAX_LOGIN_FAIL_COUNT = 5;
    private static final Duration LOGIN_FAIL_WINDOW = Duration.ofMinutes(15);

    //账号不存在时用它跑一次等价的散列校验，抹平"账号不存在"与"密码错误"的响应耗时差异
    private static final String DUMMY_PASSWORD_HASH = encodePassword(UUID.randomUUID().toString());

    //员工权限版本：角色、启用状态或密码变化时递增，令牌里记录签发时的版本，拦截器比对不一致即视为令牌失效
    private static final String AUTH_VERSION_KEY_PREFIX = "emp:auth:version:";

    @Autowired
    private EmployeeMapper employeeMapper;

    @Autowired
    private RedisTemplate redisTemplate;

    //员工登录
    public Employee login(EmployeeLoginDTO employeeLoginDTO) {
        String username = employeeLoginDTO.getUsername();
        String password = employeeLoginDTO.getPassword();

        //1、登录失败次数过多时直接拒绝，避免被持续爆破
        String failKey = LOGIN_FAIL_KEY_PREFIX + username + ":" + currentClientIp();
        if (getLoginFailCount(failKey) >= MAX_LOGIN_FAIL_COUNT) {
            throw new AccountLockedException(MessageConstant.LOGIN_FAIL_EXCEEDED);
        }

        //2、根据用户名查询数据库中的数据
        Employee employee = employeeMapper.getByUsername(username);

        //3、账号不存在时也跑一次等价的散列校验，再抛与密码错误相同的提示：
        //这样响应耗时和错误文案都无法用来判断账号是否存在（防用户名枚举）
        if (employee == null) {
            matches(password, DUMMY_PASSWORD_HASH);
            recordLoginFail(failKey);
            throw new LoginFailedException(MessageConstant.ACCOUNT_OR_PASSWORD_ERROR);
        }

        //密码比对
        if (!matches(password, employee.getPassword())) {
            recordLoginFail(failKey);
            throw new LoginFailedException(MessageConstant.ACCOUNT_OR_PASSWORD_ERROR);
        }

        //账号被禁用：放在密码校验之后，未通过密码校验的人无法据此判断账号状态
        if (Objects.equals(StatusConstant.DISABLE, employee.getStatus())) {
            //账号被锁定
            throw new AccountLockedException(MessageConstant.ACCOUNT_LOCKED);
        }

        //4、登录成功，清除失败计数；历史密文（PBKDF2/无盐MD5）在此顺带升级为Argon2id
        redisTemplate.delete(failKey);
        if (needsUpgrade(employee.getPassword())) {
            upgradePassword(employee, password);
        }

        //5、返回实体对象
        return employee;
    }

    //退出登录：递增权限版本即可让已签发的令牌在拦截器处失效，无需额外维护黑名单
    public void logout() {
        bumpAuthVersion(BaseContext.getCurrentId());
    }

    //把历史密文升级为Argon2id，升级失败不影响本次登录
    private void upgradePassword(Employee employee, String rawPassword) {
        try {
            Employee upgrade = new Employee();
            upgrade.setId(employee.getId());
            upgrade.setPassword(encodePassword(rawPassword));
            employeeMapper.update(upgrade);
        } catch (Exception e) {
            log.warn("员工{}密码升级失败，请确认 employee.password 列长度是否足够", employee.getId(), e);
        }
    }

    //新增员工
    public void save(EmployeeDTO employeeDTO) {
        //这些字段在库中为必填，先校验避免写库时才报SQL异常
        if (!StringUtils.hasText(employeeDTO.getName())) {
            throw new BaseException(MessageConstant.EMPLOYEE_NAME_IS_NULL);
        }
        if (!StringUtils.hasText(employeeDTO.getUsername())) {
            throw new BaseException(MessageConstant.EMPLOYEE_USERNAME_IS_NULL);
        }
        if (!StringUtils.hasText(employeeDTO.getPhone())) {
            throw new BaseException(MessageConstant.PHONE_IS_NULL);
        }
        if (!StringUtils.hasText(employeeDTO.getSex())) {
            throw new BaseException(MessageConstant.EMPLOYEE_SEX_IS_NULL);
        }
        if (!StringUtils.hasText(employeeDTO.getIdNumber())) {
            throw new BaseException(MessageConstant.EMPLOYEE_ID_NUMBER_IS_NULL);
        }
        //初始密码由新增员工的店长指定，不再使用人人皆知的固定默认口令
        if (!StringUtils.hasText(employeeDTO.getPassword())) {
            throw new BaseException(MessageConstant.PASSWORD_IS_NULL);
        }

        //校验用户名是否已被占用，不依赖数据库唯一索引兜底
        if (employeeMapper.getByUsername(employeeDTO.getUsername()) != null) {
            throw new BaseException(MessageConstant.ACCOUNT_ALREADY_EXISTS);
        }

        Employee employee = new Employee();

        BeanUtils.copyProperties(employeeDTO, employee);

        //设置账号的状态，默认正常状态 1表示正常 0表示锁定
        employee.setStatus(StatusConstant.ENABLE);

        //设置角色，未指定时默认为店员
        employee.setRole(employeeDTO.getRole() == null ? RoleConstant.STAFF : employeeDTO.getRole());

        //设置密码：店长指定的初始密码（加盐散列存储）
        employee.setPassword(encodePassword(employeeDTO.getPassword()));

        //设置当前记录的创建时间和修改时间
        employee.setCreateTime(LocalDateTime.now());
        employee.setUpdateTime(LocalDateTime.now());

        //设置当前记录创建人id和修改人id
        employee.setCreateUser(BaseContext.getCurrentId());
        employee.setUpdateUser(BaseContext.getCurrentId());

        employeeMapper.insert(employee);
    }

    //分页查询
    public PageResult pageQuery(EmployeePageQueryDTO employeePageQueryDTO) {
        //开始分页查询（分页参数未传或非法时按第1页、每页10条兜底）
        int pageNum = employeePageQueryDTO.getPage() < 1 ? 1 : employeePageQueryDTO.getPage();
        int pageSize = employeePageQueryDTO.getPageSize() < 1 ? 10 : employeePageQueryDTO.getPageSize();
        PageHelper.startPage(pageNum, pageSize);

        Page<Employee> page = employeeMapper.pageQuery(employeePageQueryDTO);

        long total = page.getTotal();
        List<Employee> records = page.getResult();
        //列表中不返回密码密文，避免散列值随接口响应外泄
        records.forEach(employee -> employee.setPassword("****"));

        return new PageResult(total, records);
    }

    //启用禁用员工账号
    public void startOrStop(Integer status, Long id) {
        //id 是查询参数，调用方漏传时为 null，先挡住避免下面的 id.equals 抛NPE
        if (id == null) {
            throw new BaseException(MessageConstant.EMPLOYEE_ID_IS_NULL);
        }

        //不允许禁用当前登录的员工本人
        if (id.equals(BaseContext.getCurrentId())) {
            throw new BaseException("不能对当前登录账号执行启用/禁用操作");
        }

        Employee employee = Employee.builder()
                .status(status)
                .id(id)
                .build();

        employeeMapper.update(employee);

        //启用状态被调整后，该员工此前签发的令牌立即失效
        bumpAuthVersion(id);
    }

    //根据id查询员工
    public Employee getById(Long id) {
        Employee employee = employeeMapper.getById(id);
        if (employee == null) {
            throw new AccountNotFoundException(MessageConstant.ACCOUNT_NOT_FOUND);
        }
        employee.setPassword("****");
        return employee;
    }

    //编辑员工信息
    public void update(EmployeeDTO employeeDTO) {
        //名称是必填项：传了但为空白同样拒绝（未传null表示不修改该列）
        if (employeeDTO.getName() != null && !StringUtils.hasText(employeeDTO.getName())) {
            throw new BaseException(MessageConstant.EMPLOYEE_NAME_IS_NULL);
        }

        Employee current = employeeDTO.getId() == null ? null : employeeMapper.getById(employeeDTO.getId());

        //角色保护：不允许把最后一名启用中的店长降为店员
        checkLastManagerDemotion(employeeDTO, current);

        Employee employee = new Employee();
        BeanUtils.copyProperties(employeeDTO, employee);
        //编辑员工不改密码（改密码走 editPassword 接口），置空以免把请求里带的密码写进库
        employee.setPassword(null);

        employee.setUpdateTime(LocalDateTime.now());
        employee.setUpdateUser(BaseContext.getCurrentId());

        employeeMapper.update(employee);

        //角色真的发生变化时，该员工此前签发的令牌立即失效，否则旧令牌仍带着旧角色
        if (current != null && employeeDTO.getRole() != null
                && !employeeDTO.getRole().equals(current.getRole())) {
            bumpAuthVersion(employeeDTO.getId());
        }
    }

    //把"启用中的店长"降为店员前，确认系统里还有另一名启用中的店长
    //否则改完之后没人能进员工管理、数据报表与营业状态，而员工账号又不能自助注册，等于系统锁死
    private void checkLastManagerDemotion(EmployeeDTO employeeDTO, Employee current) {
        Integer targetRole = employeeDTO.getRole();
        //role 未传表示不修改角色；仍为店长也不需要校验
        if (targetRole == null || RoleConstant.MANAGER.equals(targetRole)) {
            return;
        }
        if (current == null
                || !RoleConstant.MANAGER.equals(current.getRole())
                || !StatusConstant.ENABLE.equals(current.getStatus())) {
            return;
        }
        if (employeeMapper.countOtherEnabledManagers(current.getId()) == 0) {
            throw new BaseException(MessageConstant.MANAGER_MUST_KEEP_ONE);
        }
    }

    //修改当前员工密码
    public void editPassword(PasswordEditDTO passwordEditDTO) {
        Long currentId = BaseContext.getCurrentId();
        Employee employee = employeeMapper.getById(currentId);
        if (employee == null) {
            throw new AccountNotFoundException(MessageConstant.ACCOUNT_NOT_FOUND);
        }
        // 校验旧密码
        if (!matches(passwordEditDTO.getOldPassword(), employee.getPassword())) {
            throw new PasswordErrorException(MessageConstant.PASSWORD_ERROR);
        }
        // 设置新密码（加盐散列存储）
        employee.setPassword(encodePassword(passwordEditDTO.getNewPassword()));
        employee.setUpdateTime(LocalDateTime.now());
        employee.setUpdateUser(currentId);
        employeeMapper.update(employee);

        //改密码后，此前签发的令牌立即失效
        bumpAuthVersion(currentId);
    }

    //读取员工当前的权限版本，从未变更过的员工为0
    public int getAuthVersion(Long empId) {
        Object version = redisTemplate.opsForValue().get(AUTH_VERSION_KEY_PREFIX + empId);
        return version == null ? 0 : Integer.parseInt(version.toString());
    }

    //权限发生变化时递增版本，让该员工已签发的令牌在拦截器处立即失效
    private void bumpAuthVersion(Long empId) {
        redisTemplate.opsForValue().increment(AUTH_VERSION_KEY_PREFIX + empId);
    }

    //生成Argon2id加盐密文
    private static String encodePassword(String rawPassword) {
        byte[] salt = new byte[ARGON2_SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        byte[] hash = argon2id(rawPassword, salt, ARGON2_ITERATIONS, ARGON2_MEMORY_KB, ARGON2_PARALLELISM);
        return ARGON2_PREFIX
                + "m=" + ARGON2_MEMORY_KB + ",t=" + ARGON2_ITERATIONS + ",p=" + ARGON2_PARALLELISM + "$"
                + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(hash);
    }

    //校验密码，兼容历史的PBKDF2与无盐MD5密文
    private static boolean matches(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null || storedPassword.isEmpty()) {
            return false;
        }
        if (storedPassword.startsWith(ARGON2_PREFIX)) {
            return matchesArgon2id(rawPassword, storedPassword);
        }
        if (storedPassword.startsWith(PBKDF2_PREFIX)) {
            return matchesPbkdf2(rawPassword, storedPassword);
        }
        //最初的历史数据：无盐MD5（同样用定时安全比较，避免按比较耗时逐位试探）
        byte[] actualMd5 = DigestUtils.md5DigestAsHex(rawPassword.getBytes()).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(actualMd5, storedPassword.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean matchesArgon2id(String rawPassword, String storedPassword) {
        String[] parts = storedPassword.split("\\$");
        if (parts.length != 4) {
            return false;
        }
        //参数段形如 m=19456,t=2,p=1
        Matcher matcher = ARGON2_PARAM_PATTERN.matcher(parts[1]);
        if (!matcher.matches()) {
            return false;
        }
        byte[] salt = Base64.getDecoder().decode(parts[2]);
        byte[] expected = Base64.getDecoder().decode(parts[3]);
        byte[] actual = argon2id(rawPassword, salt,
                Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(3)));
        //使用定时安全比较，避免根据比较耗时推断密文
        return MessageDigest.isEqual(expected, actual);
    }

    //历史PBKDF2密文的校验
    private static boolean matchesPbkdf2(String rawPassword, String storedPassword) {
        String[] parts = storedPassword.split("\\$");
        if (parts.length != 4) {
            return false;
        }
        byte[] salt = Base64.getDecoder().decode(parts[2]);
        byte[] expected = Base64.getDecoder().decode(parts[3]);
        byte[] actual = pbkdf2(rawPassword, salt, Integer.parseInt(parts[1]));
        return MessageDigest.isEqual(expected, actual);
    }

    //是否为待升级的历史密文（非Argon2id）
    private static boolean needsUpgrade(String storedPassword) {
        return storedPassword == null || !storedPassword.startsWith(ARGON2_PREFIX);
    }

    //Argon2id 散列
    private static byte[] argon2id(String rawPassword, byte[] salt, int iterations, int memoryKb, int parallelism) {
        Argon2Parameters parameters = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withIterations(iterations)
                .withMemoryAsKB(memoryKb)
                .withParallelism(parallelism)
                .withSalt(salt)
                .build();
        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(parameters);
        byte[] hash = new byte[ARGON2_HASH_BYTES];
        generator.generateBytes(rawPassword.toCharArray(), hash);
        return hash;
    }

    //PBKDF2加盐迭代散列（仅用于校验历史密文）
    private static byte[] pbkdf2(String rawPassword, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(rawPassword.toCharArray(), salt, iterations, PBKDF2_HASH_BITS);
            return SecretKeyFactory.getInstance(PBKDF2_ALGORITHM).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("密码散列失败", e);
        }
    }

    //读取当前登录失败次数
    private long getLoginFailCount(String failKey) {
        Object count = redisTemplate.opsForValue().get(failKey);
        return count == null ? 0L : Long.parseLong(count.toString());
    }

    //记录一次登录失败，并刷新锁定窗口
    private void recordLoginFail(String failKey) {
        redisTemplate.opsForValue().increment(failKey);
        redisTemplate.expire(failKey, LOGIN_FAIL_WINDOW);
    }

    //取调用方IP用于登录失败计数：优先取反向代理写入的 X-Forwarded-For 首段
    //XFF 可被伪造，但那只会让攻击者绕过对自己的锁定，比"用固定IP把别人锁死"危害小
    private static String currentClientIp() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "unknown";
        }
        HttpServletRequest request = attributes.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            int comma = forwarded.indexOf(',');
            return comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
        }
        return request.getRemoteAddr();
    }
}

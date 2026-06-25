package com.example.exceptionhandling;

import com.example.errors.OracleErrorExtractor;
import com.example.errors.OracleDataAccessException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class OracleExceptionAspect {
    // Wrap JPA repositories or other packages/classes handling db operations
    @Around("within(com.example.exceptionhandling..*)")
    public Object translateOracleJpaExceptions(ProceedingJoinPoint joinPoint) throws Throwable {
        try {
            return joinPoint.proceed();
        } catch (DataAccessException exception) { // This exception wraps exceptions thrown by DAOs like JPA repositories,
            // including SQLException
            throw OracleErrorExtractor.from(exception)
                    .map(oracleError ->
                            // Put your specific error handling logic here
                            new OracleDataAccessException(oracleError, exception)
                    )
                    .orElseThrow(() -> exception);
        }
    }
}

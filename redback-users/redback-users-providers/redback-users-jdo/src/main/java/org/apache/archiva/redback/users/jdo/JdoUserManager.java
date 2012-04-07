package org.apache.archiva.redback.users.jdo;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.archiva.redback.policy.UserSecurityPolicy;
import org.apache.archiva.redback.users.AbstractUserManager;
import org.apache.archiva.redback.users.User;
import org.apache.archiva.redback.users.UserManagerException;
import org.apache.archiva.redback.users.UserNotFoundException;
import org.codehaus.plexus.jdo.JdoFactory;
import org.codehaus.plexus.jdo.PlexusJdoUtils;
import org.codehaus.plexus.jdo.PlexusObjectNotFoundException;
import org.codehaus.plexus.jdo.PlexusStoreException;
import org.apache.archiva.redback.users.PermanentUserException;
import org.apache.archiva.redback.users.UserQuery;
import org.codehaus.plexus.redback.users.jdo.JdoUser;
import org.codehaus.plexus.redback.users.jdo.UsersManagementModelloMetadata;
import org.codehaus.plexus.util.StringUtils;
import org.jpox.JDOClassLoaderResolver;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Named;
import javax.jdo.Extent;
import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;
import javax.jdo.Query;
import javax.jdo.Transaction;

import java.util.Date;
import java.util.List;

/**
 * JdoUserManager
 *
 * @author <a href="mailto:joakim@erdfelt.com">Joakim Erdfelt</a>
 * @version $Id$
 */
@Service("userManager#jdo")
public class JdoUserManager
    extends AbstractUserManager
{
    @Inject @Named(value="jdoFactory#users")
    private JdoFactory jdoFactory;

    @Inject
    private UserSecurityPolicy userSecurityPolicy;

    private PersistenceManagerFactory pmf;

    public String getId()
    {
        return "JDO UserManager - " + this.getClass().getName();
    }


    public boolean isReadOnly()
    {
        return false;
    }

    public UserQuery createUserQuery()
    {
        return new JdoUserQuery();
    }

    // ------------------------------------------------------------------

    public User createUser( String username, String fullname, String email )
    {
        User user = new JdoUser();
        user.setUsername( username );
        user.setFullName( fullname );
        user.setEmail( email );
        user.setAccountCreationDate( new Date() );

        return user;
    }

    public List<User> getUsers()
    {
        return getAllObjectsDetached( null );
    }

    public List<User> getUsers( boolean orderAscending )
    {
        String ordering = orderAscending ? "username ascending" : "username descending";

        return getAllObjectsDetached( ordering );
    }

    @SuppressWarnings("unchecked")
    private List<User> getAllObjectsDetached( String ordering )
    {
        return PlexusJdoUtils.getAllObjectsDetached( getPersistenceManager(), JdoUser.class, ordering, (String) null );
    }

    public List<User> findUsersByUsernameKey( String usernameKey, boolean orderAscending )
    {
        return findUsers( "username", usernameKey, orderAscending );
    }

    public List<User> findUsersByFullNameKey( String fullNameKey, boolean orderAscending )
    {
        return findUsers( "fullName", fullNameKey, orderAscending );
    }

    public List<User> findUsersByEmailKey( String emailKey, boolean orderAscending )
    {
        return findUsers( "email", emailKey, orderAscending );
    }

    @SuppressWarnings("unchecked")
    public List<User> findUsersByQuery( UserQuery userQuery )
    {
        JdoUserQuery uq = (JdoUserQuery) userQuery;

        PersistenceManager pm = getPersistenceManager();

        Transaction tx = pm.currentTransaction();

        try
        {
            tx.begin();

            Extent extent = pm.getExtent( JdoUser.class, true );

            Query query = pm.newQuery( extent );

            String ordering = uq.getOrdering();

            query.setOrdering( ordering );

            query.declareImports( "import java.lang.String" );

            query.declareParameters( uq.getParameters() );

            query.setFilter( uq.getFilter() );

            query.setRange( uq.getFirstResult(),
                            uq.getMaxResults() < 0 ? Long.MAX_VALUE : uq.getFirstResult() + uq.getMaxResults() );

            List<User> result = (List<User>) query.executeWithArray( uq.getSearchKeys() );

            result = (List<User>) pm.detachCopyAll( result );

            tx.commit();

            return result;
        }
        finally
        {
            rollback( tx );
        }
    }

    @SuppressWarnings("unchecked")
    private List<User> findUsers( String searchField, String searchKey, boolean ascendingUsername )
    {
        PersistenceManager pm = getPersistenceManager();

        Transaction tx = pm.currentTransaction();

        try
        {
            tx.begin();

            Extent extent = pm.getExtent( JdoUser.class, true );

            Query query = pm.newQuery( extent );

            String ordering = ascendingUsername ? "username ascending" : "username descending";

            query.setOrdering( ordering );

            query.declareImports( "import java.lang.String" );

            query.declareParameters( "String searchKey" );

            query.setFilter( "this." + searchField + ".toLowerCase().indexOf(searchKey.toLowerCase()) > -1" );

            List<User> result = (List<User>) query.execute( searchKey );

            result = (List<User>) pm.detachCopyAll( result );

            tx.commit();

            return result;
        }
        finally
        {
            rollback( tx );
        }
    }

    public User addUser( User user )
    {
        if ( !( user instanceof JdoUser ) )
        {
            throw new UserManagerException( "Unable to Add User. User object " + user.getClass().getName() +
                " is not an instance of " + JdoUser.class.getName() );
        }

        if ( StringUtils.isEmpty( user.getUsername() ) )
        {
            throw new IllegalStateException(
                Messages.getString( "user.manager.cannot.add.user.without.username" ) ); //$NON-NLS-1$
        }

        userSecurityPolicy.extensionChangePassword( user );

        fireUserManagerUserAdded( user );

        // TODO: find a better solution
        // workaround for avoiding the admin from providing another password on the next login after the
        // admin account has been created
        // extensionChangePassword by default sets the password change status to false
        if ( "admin".equals( user.getUsername() ) )
        {
            user.setPasswordChangeRequired( false );
        }
        else
        {
            user.setPasswordChangeRequired( true );
        }

        return (User) addObject( user );
    }

    public void deleteUser( Object principal )
    {
        try
        {
            User user = findUser( principal );

            if ( user.isPermanent() )
            {
                throw new PermanentUserException( "Cannot delete permanent user [" + user.getUsername() + "]." );
            }

            fireUserManagerUserRemoved( user );

            removeObject( user );
        }
        catch ( UserNotFoundException e )
        {
            log.warn( "Unable to delete user " + principal + ", user not found.", e );
        }
    }

    public void deleteUser( String username )
    {
        try
        {
            User user = findUser( username );

            if ( user.isPermanent() )
            {
                throw new PermanentUserException( "Cannot delete permanent user [" + user.getUsername() + "]." );
            }

            fireUserManagerUserRemoved( user );

            PlexusJdoUtils.removeObject( getPersistenceManager(), user );
        }
        catch ( UserNotFoundException e )
        {
            log.warn( "Unable to delete user " + username + ", user not found.", e );
        }
    }

    public void addUserUnchecked( User user )
    {
        if ( !( user instanceof JdoUser ) )
        {
            throw new UserManagerException( "Unable to Add User. User object " + user.getClass().getName() +
                " is not an instance of " + JdoUser.class.getName() );
        }

        if ( StringUtils.isEmpty( user.getUsername() ) )
        {
            throw new IllegalStateException(
                Messages.getString( "user.manager.cannot.add.user.without.username" ) ); //$NON-NLS-1$
        }

        addObject( user );
    }

    public void eraseDatabase()
    {
        PlexusJdoUtils.removeAll( getPersistenceManager(), JdoUser.class );
        PlexusJdoUtils.removeAll( getPersistenceManager(), UsersManagementModelloMetadata.class );
    }

    public User findUser( Object principal )
        throws UserNotFoundException
    {
        if ( principal == null )
        {
            throw new UserNotFoundException( "Unable to find user based on null principal." );
        }

        try
        {
            return (User) PlexusJdoUtils.getObjectById( getPersistenceManager(), JdoUser.class, principal.toString(),
                                                        null );
        }
        catch ( PlexusObjectNotFoundException e )
        {
            throw new UserNotFoundException( "Unable to find user: " + e.getMessage(), e );
        }
        catch ( PlexusStoreException e )
        {
            throw new UserNotFoundException( "Unable to find user: " + e.getMessage(), e );
        }
    }

    public User findUser( String username )
        throws UserNotFoundException
    {
        if ( StringUtils.isEmpty( username ) )
        {
            throw new UserNotFoundException( "User with empty username not found." );
        }

        return (User) getObjectById( username, null );
    }

    public boolean userExists( Object principal )
    {
        try
        {
            findUser( principal );
            return true;
        }
        catch ( UserNotFoundException ne )
        {
            return false;
        }
    }

    public User updateUser( User user )
        throws UserNotFoundException
    {
        return updateUser( user, false );
    }

    public User updateUser( User user, boolean passwordChangeRequired )
        throws UserNotFoundException
    {
        if ( !( user instanceof JdoUser ) )
        {
            throw new UserManagerException( "Unable to Update User. User object " + user.getClass().getName() +
                " is not an instance of " + JdoUser.class.getName() );
        }

        // If password is supplied, assume changing of password.
        // TODO: Consider adding a boolean to the updateUser indicating a password change or not.
        if ( StringUtils.isNotEmpty( user.getPassword() ) )
        {
            userSecurityPolicy.extensionChangePassword( user, passwordChangeRequired );
        }

        updateObject( user );

        fireUserManagerUserUpdated( user );

        return user;
    }

    @PostConstruct
    public void initialize()
    {
        JDOClassLoaderResolver d;
        pmf = jdoFactory.getPersistenceManagerFactory();
    }

    public PersistenceManager getPersistenceManager()
    {
        PersistenceManager pm = pmf.getPersistenceManager();

        pm.getFetchPlan().setMaxFetchDepth( -1 );

        triggerInit();

        return pm;
    }

    // ----------------------------------------------------------------------
    // jdo utility methods
    // ----------------------------------------------------------------------

    private Object addObject( Object object )
    {
        return PlexusJdoUtils.addObject( getPersistenceManager(), object );
    }

    private Object getObjectById( String id, String fetchGroup )
        throws UserNotFoundException, UserManagerException
    {
        try
        {
            return PlexusJdoUtils.getObjectById( getPersistenceManager(), JdoUser.class, id, fetchGroup );
        }
        catch ( PlexusObjectNotFoundException e )
        {
            throw new UserNotFoundException( e.getMessage() );
        }
        catch ( PlexusStoreException e )
        {
            throw new UserManagerException( "Unable to get object '" + JdoUser.class.getName() + "', id '" + id +
                "', fetch-group '" + fetchGroup + "' from jdo store." );
        }
    }

    private Object removeObject( Object o )
    {
        if ( o == null )
        {
            throw new UserManagerException( "Unable to remove null object" );
        }

        PlexusJdoUtils.removeObject( getPersistenceManager(), o );
        return o;
    }

    private Object updateObject( Object object )
        throws UserNotFoundException, UserManagerException
    {
        try
        {
            return PlexusJdoUtils.updateObject( getPersistenceManager(), object );
        }
        catch ( PlexusStoreException e )
        {
            throw new UserManagerException(
                "Unable to update the '" + object.getClass().getName() + "' object in the jdo database.", e );
        }
    }

    private void rollback( Transaction tx )
    {
        PlexusJdoUtils.rollbackIfActive( tx );
    }

    private boolean hasTriggeredInit = false;

    public void triggerInit()
    {
        if ( !hasTriggeredInit )
        {
            hasTriggeredInit = true;
            List<User> users = getAllObjectsDetached( null );

            fireUserManagerInit( users.isEmpty() );
        }
    }

    public JdoFactory getJdoFactory()
    {
        return jdoFactory;
    }

    public void setJdoFactory( JdoFactory jdoFactory )
    {
        this.jdoFactory = jdoFactory;
    }

    public UserSecurityPolicy getUserSecurityPolicy()
    {
        return userSecurityPolicy;
    }
}
